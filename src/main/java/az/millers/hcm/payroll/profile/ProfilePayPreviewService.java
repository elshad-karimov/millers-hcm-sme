package az.millers.hcm.payroll.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.timepay.PeriodNormHours;
import az.millers.hcm.payroll.timepay.PeriodNormHoursRepository;
import az.millers.hcm.payroll.timepay.TimePayRule;
import az.millers.hcm.payroll.timepay.TimePayRuleOverride;
import az.millers.hcm.payroll.timepay.TimePayRuleOverrideRepository;
import az.millers.hcm.payroll.timepay.TimePayRuleRepository;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetMonthTotal;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.repo.TimesheetMonthTotalRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * Profile-aware pricing of an approved month — read only.
 *
 * <p>Creates no payroll run, no result and no payslip, and does not touch
 * {@code PayrollEngine}. It exists so the four contract types can be checked
 * against the company's own spreadsheets before any money moves.
 *
 * <p>Only APPROVED and LOCKED months are priced. Payroll reads a locked
 * attendance summary or it does not run: pricing a draft month would put a
 * number in front of payroll that nobody has stood behind.
 */
@Service
public class ProfilePayPreviewService {

    private static final Set<TimesheetStatus> PRICEABLE =
            Set.of(TimesheetStatus.APPROVED, TimesheetStatus.LOCKED);

    private final TimesheetRepository timesheets;
    private final TimesheetMonthTotalRepository monthTotals;
    private final EmployeeRepository employees;
    private final EmployeeCompensationRepository compensations;
    private final TimePayRuleRepository payRules;
    private final TimePayRuleOverrideRepository overrides;
    private final PeriodNormHoursRepository normHours;
    private final CalculationProfileRepository profiles;
    private final EmployeeCalculationProfileRepository assignments;
    private final EmployeeMewaRuleRepository mewaRules;
    private final ExcessAccumulatorService accumulator;
    private final ProfilePayCalculator calculator;

    public ProfilePayPreviewService(TimesheetRepository timesheets,
                                    TimesheetMonthTotalRepository monthTotals,
                                    EmployeeRepository employees,
                                    EmployeeCompensationRepository compensations,
                                    TimePayRuleRepository payRules,
                                    TimePayRuleOverrideRepository overrides,
                                    PeriodNormHoursRepository normHours,
                                    CalculationProfileRepository profiles,
                                    EmployeeCalculationProfileRepository assignments,
                                    EmployeeMewaRuleRepository mewaRules,
                                    ExcessAccumulatorService accumulator,
                                    ProfilePayCalculator calculator) {
        this.timesheets = timesheets;
        this.monthTotals = monthTotals;
        this.employees = employees;
        this.compensations = compensations;
        this.payRules = payRules;
        this.overrides = overrides;
        this.normHours = normHours;
        this.profiles = profiles;
        this.assignments = assignments;
        this.mewaRules = mewaRules;
        this.accumulator = accumulator;
        this.calculator = calculator;
    }

    public record EmployeePreview(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            String positionTitle,
            String timesheetStatus,
            String profileCode,
            BigDecimal baseSalary,
            BigDecimal normHours,
            Map<String, BigDecimal> quantities,
            BigDecimal settlementExcessHours,
            ProfilePayCalculator.Result result,
            List<String> blockers) {
    }

    public record PeriodPreview(
            int year,
            int month,
            BigDecimal normHours,
            int priced,
            int notPriceable,
            int unassigned,
            BigDecimal totalGross,
            BigDecimal totalNet,
            int withBlockers,
            List<EmployeePreview> employees,
            /** Employees whose month could not be priced at all, and why. */
            Map<String, String> skipped) {
    }

    @Transactional(readOnly = true)
    public PeriodPreview period(int year, int month) {
        BigDecimal norm = normHoursFor(year, month);
        List<Timesheet> all =
                timesheets.findByPeriodYearAndPeriodMonthOrderByEmployeeIdAsc(year, month);

        Map<UUID, Employee> employeeById = employees.findAllById(
                        all.stream().map(Timesheet::getEmployeeId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));

        List<EmployeePreview> previews = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        int notPriceable = 0;
        int unassigned = 0;

        for (Timesheet ts : all) {
            if (!PRICEABLE.contains(ts.getStatus())) {
                notPriceable++;
                continue;
            }
            try {
                EmployeePreview p = price(ts, employeeById.get(ts.getEmployeeId()), norm, year, month);
                previews.add(p);
                gross = gross.add(p.result().gross());
                net = net.add(p.result().netPay());
            } catch (IllegalArgumentException | BadRequestException e) {
                // One employee's missing configuration must not hide the rest of
                // the period. Name it and carry on.
                unassigned++;
                skipped.put(ts.getEmployeeId().toString(), e.getMessage());
            }
        }

        return new PeriodPreview(year, month, norm, previews.size(), notPriceable, unassigned,
                gross, net,
                (int) previews.stream().filter(p -> !p.blockers().isEmpty()).count(),
                previews, skipped);
    }

    @Transactional(readOnly = true)
    public EmployeePreview employee(int year, int month, UUID employeeId) {
        Timesheet ts = timesheets
                .findByEmployeeIdAndPeriodYearAndPeriodMonth(employeeId, year, month)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No timesheet for that employee in " + year + "-" + month + "."));
        if (!PRICEABLE.contains(ts.getStatus())) {
            throw new BadRequestException("This timesheet is " + ts.getStatus()
                    + ". Only approved or locked months can be priced — payroll must not "
                    + "consume quantities nobody has approved.");
        }
        return price(ts, employees.findById(employeeId).orElse(null),
                normHoursFor(year, month), year, month);
    }

    /** The excess ledger behind a rotation employee's settlement. */
    @Transactional(readOnly = true)
    public List<ExcessAccumulatorService.Ledger> excessLedger(UUID employeeId) {
        return accumulator.ledgersFor(employeeId);
    }

    // ---------- Internals ----------

    private EmployeePreview price(Timesheet ts, Employee employee, BigDecimal norm,
                                  int year, int month) {
        UUID employeeId = ts.getEmployeeId();
        LocalDate periodStart = YearMonth.of(year, month).atDay(1);
        List<String> blockers = new ArrayList<>();

        CalculationProfile profile = profileFor(employeeId, periodStart);

        BigDecimal base = compensations.findActiveOn(employeeId, periodStart)
                .map(EmployeeCompensation::getMonthlyBaseSalary)
                .orElse(null);
        if (base == null) {
            blockers.add("No effective compensation record — base salary is the source of "
                    + "every rate, so nothing can be priced.");
            base = BigDecimal.ZERO;
        }

        Map<String, BigDecimal> quantities = monthTotals
                .findByTimesheetIdOrderByCategoryCodeAsc(ts.getId()).stream()
                .collect(Collectors.toMap(TimesheetMonthTotal::getCategoryCode,
                        TimesheetMonthTotal::getQuantity, BigDecimal::add, LinkedHashMap::new));

        BigDecimal settlementHours = settlementHoursFor(profile, employeeId, year, month);

        ProfilePayCalculator.Result result = calculator.calculate(
                new ProfilePayCalculator.Input(
                        profile, base, norm, periodStart, quantities,
                        rulesFor(employeeId, periodStart),
                        mewaRateFor(employeeId, periodStart),
                        settlementHours,
                        // Manual columns have no home in the timesheet. Vacation
                        // pay in particular is an average-earnings calculation,
                        // not something this month's hours can produce.
                        null, null, null, null, null, null));

        blockers.addAll(result.blockers());

        return new EmployeePreview(
                employeeId,
                employee == null ? null : employee.getEmployeeNo(),
                employee == null ? null : employee.getLastName() + ", " + employee.getFirstName(),
                employee == null ? null : employee.getPositionTitle(),
                ts.getStatus().name(),
                profile.getCode(),
                base, norm, quantities, settlementHours, result, blockers);
    }

    private CalculationProfile profileFor(UUID employeeId, LocalDate periodStart) {
        String code = assignments.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .filter(a -> a.coversPeriodStart(periodStart))
                .findFirst()
                .map(EmployeeCalculationProfile::getProfileCode)
                .orElseThrow(() -> new BadRequestException(
                        "This employee has no calculation profile effective on " + periodStart
                                + ". Pay cannot be derived without one: the same hours are "
                                + "worth different amounts under different contracts, so "
                                + "defaulting would be a guess at someone's salary."));

        return profiles.findByCode(code).orElseThrow(() -> new BadRequestException(
                "Calculation profile '" + code + "' is assigned to this employee but does not "
                        + "exist."));
    }

    /** Only a settlement month releases hours, and only for a balancing profile. */
    private BigDecimal settlementHoursFor(CalculationProfile profile, UUID employeeId,
                                          int year, int month) {
        if (!profile.settlesExcessOverBalancingPeriod()
                || profile.getBalancingSchemeCode() == null) {
            return null;
        }
        Optional<BigDecimal> due = accumulator.dueThisMonth(
                employeeId, profile.getBalancingSchemeCode(), year, month);
        return due.filter(h -> h.signum() > 0).orElse(null);
    }

    /** Catalog rules with this employee's dated overrides merged in. */
    private List<TimePayRule> rulesFor(UUID employeeId, LocalDate periodStart) {
        Map<String, TimePayRule> byCode = payRules.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .collect(Collectors.toMap(TimePayRule::getCategoryCode, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));

        for (TimePayRuleOverride o : overrides.findByEmployeeId(employeeId)) {
            if (!o.coversPeriodStart(periodStart)) continue;
            TimePayRule base = byCode.get(o.getCategoryCode());
            if (base == null) continue;
            byCode.put(o.getCategoryCode(), o.applyTo(base));
        }
        return List.copyOf(byCode.values());
    }

    private BigDecimal mewaRateFor(UUID employeeId, LocalDate periodStart) {
        return mewaRules.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .filter(r -> r.coversPeriodStart(periodStart))
                .findFirst()
                .map(EmployeeMewaRule::getRate)
                .orElse(null);
    }

    private BigDecimal normHoursFor(int year, int month) {
        return normHours.findByPeriodYearAndPeriodMonth(year, month)
                .map(PeriodNormHours::getNormHours)
                .orElseThrow(() -> new BadRequestException(
                        "Norm working hours are not configured for "
                                + year + "-" + String.format("%02d", month)
                                + ". Every hourly rate divides by it, so nothing can be priced "
                                + "until it is set."));
    }
}
