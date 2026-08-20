package az.millers.hcm.payroll.timepay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetMonthTotal;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.repo.TimesheetMonthTotalRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * Prices a period's approved timesheets — read only.
 *
 * <p>Creates no payroll run, no result and no payslip. It exists so the numbers
 * can be compared against the January 2026 workbook line by line before any
 * money moves, which is the only responsible way to replace a spreadsheet that
 * has been paying people.
 *
 * <p>Only APPROVED and LOCKED months are priced. A draft or submitted month has
 * not been judged by anyone, and pricing it would put a number in front of
 * payroll that nobody has stood behind.
 */
@Service
public class TimesheetPayPreviewService {

    /** The only states whose quantities anyone has vouched for. */
    private static final Set<TimesheetStatus> PRICEABLE =
            Set.of(TimesheetStatus.APPROVED, TimesheetStatus.LOCKED);

    private final TimesheetRepository timesheets;
    private final TimesheetMonthTotalRepository monthTotals;
    private final EmployeeRepository employees;
    private final EmployeeCompensationRepository compensations;
    private final TimePayRuleRepository payRules;
    private final TimePayRuleOverrideRepository overrides;
    private final EmployeeExcessRuleRepository excessRules;
    private final PeriodNormHoursRepository normHours;
    private final TimesheetPayCalculator calculator;

    public TimesheetPayPreviewService(TimesheetRepository timesheets,
                                      TimesheetMonthTotalRepository monthTotals,
                                      EmployeeRepository employees,
                                      EmployeeCompensationRepository compensations,
                                      TimePayRuleRepository payRules,
                                      TimePayRuleOverrideRepository overrides,
                                      EmployeeExcessRuleRepository excessRules,
                                      PeriodNormHoursRepository normHours,
                                      TimesheetPayCalculator calculator) {
        this.timesheets = timesheets;
        this.monthTotals = monthTotals;
        this.employees = employees;
        this.compensations = compensations;
        this.payRules = payRules;
        this.overrides = overrides;
        this.excessRules = excessRules;
        this.normHours = normHours;
        this.calculator = calculator;
    }

    /** One employee's priced month, with every input shown next to the result. */
    public record EmployeePreview(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            String positionTitle,
            String timesheetStatus,
            BigDecimal baseSalary,
            BigDecimal normHours,
            Map<String, BigDecimal> quantities,
            TimesheetPayCalculator.Result result,
            List<String> blockers) {
    }

    public record PeriodPreview(
            int year,
            int month,
            BigDecimal normHours,
            int priceable,
            int notPriceable,
            BigDecimal totalGross,
            BigDecimal totalNet,
            int withBlockers,
            List<EmployeePreview> employees) {
    }

    @Transactional(readOnly = true)
    public PeriodPreview period(int year, int month) {
        BigDecimal norm = normHoursFor(year, month);
        List<Timesheet> all = timesheets.findByPeriodYearAndPeriodMonthOrderByEmployeeIdAsc(year, month);

        Map<UUID, Employee> employeeById = employees.findAllById(
                        all.stream().map(Timesheet::getEmployeeId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));

        List<EmployeePreview> previews = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        int notPriceable = 0;

        for (Timesheet ts : all) {
            if (!PRICEABLE.contains(ts.getStatus())) {
                notPriceable++;
                continue;
            }
            EmployeePreview p = price(ts, employeeById.get(ts.getEmployeeId()), norm, year, month);
            previews.add(p);
            gross = gross.add(p.result().gross());
            net = net.add(p.result().netPay());
        }

        return new PeriodPreview(year, month, norm, previews.size(), notPriceable,
                gross, net,
                (int) previews.stream().filter(p -> !p.blockers().isEmpty()).count(),
                previews);
    }

    @Transactional(readOnly = true)
    public EmployeePreview employee(int year, int month, UUID employeeId) {
        Timesheet ts = timesheets.findByEmployeeIdAndPeriodYearAndPeriodMonth(employeeId, year, month)
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

    // ---------- Internals ----------

    private EmployeePreview price(Timesheet ts, Employee employee, BigDecimal norm,
                                  int year, int month) {
        LocalDate periodStart = YearMonth.of(year, month).atDay(1);
        List<String> blockers = new ArrayList<>();

        BigDecimal base = compensations.findActiveOn(ts.getEmployeeId(), periodStart)
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

        TimesheetPayCalculator.Result result = calculator.calculate(
                new TimesheetPayCalculator.Input(
                        base, norm, periodStart, quantities,
                        rulesFor(ts.getEmployeeId(), periodStart),
                        excessRuleFor(ts.getEmployeeId(), periodStart),
                        // The workbook's manual columns have no home in the
                        // timesheet, so they are zero until someone enters them.
                        null, null, null, null, null, null));

        blockers.addAll(result.warnings());

        return new EmployeePreview(
                ts.getEmployeeId(),
                employee == null ? null : employee.getEmployeeNo(),
                employee == null ? null : employee.getLastName() + ", " + employee.getFirstName(),
                employee == null ? null : employee.getPositionTitle(),
                ts.getStatus().name(),
                base, norm, quantities, result, blockers);
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

    private EmployeeExcessRule excessRuleFor(UUID employeeId, LocalDate periodStart) {
        return excessRules.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .filter(r -> r.coversPeriodStart(periodStart))
                .findFirst()
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
