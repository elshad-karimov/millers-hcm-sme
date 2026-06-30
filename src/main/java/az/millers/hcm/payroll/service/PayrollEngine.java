package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.attendance.service.AttendanceDeductionBridge;
import az.millers.hcm.attendance.service.AttendanceDeductionBridge.AttendanceDeductionResult;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.compbenefits.domain.AllowanceStatus;
import az.millers.hcm.compbenefits.domain.AllowanceType;
import az.millers.hcm.compbenefits.domain.EmployeeAllowance;
import az.millers.hcm.compbenefits.repo.AllowanceTypeRepository;
import az.millers.hcm.compbenefits.repo.EmployeeAllowanceRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.CalculationMethod;
import az.millers.hcm.payroll.domain.ComponentKind;
import az.millers.hcm.payroll.domain.PayrollAllowance;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollResultComponent;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollDeduction;
import az.millers.hcm.payroll.domain.SalaryComponent;
import az.millers.hcm.payroll.domain.SalaryComponentAssignment;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.repo.PayrollAllowanceRepository;
import az.millers.hcm.payroll.repo.PayrollBonusRepository;
import az.millers.hcm.payroll.repo.PayrollDeductionRepository;
import az.millers.hcm.payroll.repo.PayrollResultComponentRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunHoldRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.payroll.repo.SalaryComponentAssignmentRepository;
import az.millers.hcm.payroll.service.StatutoryCalculator.ContributionPair;
import az.millers.hcm.payroll.service.StatutoryCalculator.IncomeTaxResult;
import az.millers.hcm.payroll.service.StatutoryCalculator.OvertimePay;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetDay;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.repo.TimesheetDayRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * Orchestrates the payroll calculation for one run.
 * Consumes APPROVED timesheets only — drafts and submitted ones are skipped.
 */
@Service
public class PayrollEngine {

    private static final Logger log = LoggerFactory.getLogger(PayrollEngine.class);

    /**
     * Employment statuses eligible to be included in a payroll run (M61 / P1-02).
     * Statuses outside this set (TERMINATED, RETIRED, …) have their stale
     * APPROVED timesheets silently skipped — the engine logs each skip so HR
     * can audit the decision. A separate final-settlement path covers payouts
     * to terminated employees.
     */
    private static final Set<EmploymentStatus> PAYABLE_STATUSES = EnumSet.of(
            EmploymentStatus.ACTIVE,
            EmploymentStatus.ON_PROBATION,
            EmploymentStatus.ON_LEAVE,
            EmploymentStatus.ON_BUSINESS_TRIP,
            EmploymentStatus.CONTRACTOR,
            EmploymentStatus.INTERN);

    private final PayrollRunRepository runs;
    private final PayrollResultRepository results;
    private final PayrollBonusRepository bonuses;
    private final PayrollAllowanceRepository allowanceSnapshots;
    private final TimesheetRepository timesheets;
    private final TimesheetDayRepository timesheetDays;
    private final EmployeeCompensationRepository compensations;
    private final EmployeeAllowanceRepository employeeAllowances;
    private final AllowanceTypeRepository allowanceTypes;
    private final EmployeeRepository employees;
    private final PayrollDeductionRepository deductions;
    private final AttendanceDeductionBridge attendanceBridge;
    private final StatutoryCalculator calculator;
    private final SalaryComponentAssignmentRepository componentAssignments;
    private final PayrollResultComponentRepository resultComponents;
    private final PayrollRunHoldRepository holdRepo;
    private final PayrollAdvanceLoanDeductionService advanceLoanDeductionService;
    private final ObjectMapper objectMapper;

    public PayrollEngine(PayrollRunRepository runs,
                          PayrollResultRepository results,
                          PayrollBonusRepository bonuses,
                          PayrollAllowanceRepository allowanceSnapshots,
                          TimesheetRepository timesheets,
                          TimesheetDayRepository timesheetDays,
                          EmployeeCompensationRepository compensations,
                          EmployeeAllowanceRepository employeeAllowances,
                          AllowanceTypeRepository allowanceTypes,
                          EmployeeRepository employees,
                          PayrollDeductionRepository deductions,
                          AttendanceDeductionBridge attendanceBridge,
                          StatutoryCalculator calculator,
                          SalaryComponentAssignmentRepository componentAssignments,
                          PayrollResultComponentRepository resultComponents,
                          PayrollRunHoldRepository holdRepo,
                          PayrollAdvanceLoanDeductionService advanceLoanDeductionService,
                          ObjectMapper objectMapper) {
        this.runs = runs;
        this.results = results;
        this.bonuses = bonuses;
        this.allowanceSnapshots = allowanceSnapshots;
        this.timesheets = timesheets;
        this.timesheetDays = timesheetDays;
        this.compensations = compensations;
        this.employeeAllowances = employeeAllowances;
        this.allowanceTypes = allowanceTypes;
        this.employees = employees;
        this.deductions = deductions;
        this.attendanceBridge = attendanceBridge;
        this.calculator = calculator;
        this.componentAssignments = componentAssignments;
        this.resultComponents = resultComponents;
        this.holdRepo = holdRepo;
        this.advanceLoanDeductionService = advanceLoanDeductionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PayrollRun calculate(PayrollRun run) {
        // Clear previous results + allowance snapshots + component snapshots so re-running
        // stays idempotent. Bonus rows are intentionally NOT wiped — they're
        // added explicitly via AddBonusRequest and should survive a recalc.
        results.deleteByRunId(run.getId());
        allowanceSnapshots.deleteByRunId(run.getId());
        resultComponents.deleteByResultId(run.getId());
        results.flush();

        LocalDate ruleDate = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).atDay(1);
        List<Timesheet> approved = timesheets.findByPeriodYearAndPeriodMonthAndStatus(
                run.getPeriodYear(), run.getPeriodMonth(), TimesheetStatus.APPROVED);

        // M350: OFF_CYCLE filtering — restrict to specific employees
        if (run.getRunType() == az.millers.hcm.payroll.domain.RunType.OFF_CYCLE
                && run.getEmployeeIds() != null && !run.getEmployeeIds().isEmpty()) {
            Set<UUID> targetIds = new HashSet<>(run.getEmployeeIds());
            approved = approved.stream()
                    .filter(ts -> targetIds.contains(ts.getEmployeeId()))
                    .toList();
        }

        if (approved.isEmpty()) {
            throw new BadRequestException(
                    "No APPROVED timesheets for " + run.getPeriodYear() + "/" + run.getPeriodMonth()
                            + ". Approve the timesheets before running payroll.");
        }

        // M41: AllowanceType lookup gets called once per allowance row per
        // employee — cache the catalogue up-front so we don't hammer the DB.
        Map<UUID, AllowanceType> typesById = new HashMap<>();
        for (AllowanceType t : allowanceTypes.findAll()) typesById.put(t.getId(), t);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalIncomeTax = BigDecimal.ZERO;
        BigDecimal totalDsmfEmp = BigDecimal.ZERO, totalDsmfEr = BigDecimal.ZERO;
        BigDecimal totalMmiEmp = BigDecimal.ZERO, totalMmiEr = BigDecimal.ZERO;
        BigDecimal totalUnEmp = BigDecimal.ZERO, totalUnEr = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalAllowance = BigDecimal.ZERO;

        for (Timesheet ts : approved) {
            UUID employeeId = ts.getEmployeeId();

            // M350: skip if employee is on hold for this run
            if (holdRepo.existsByRunIdAndEmployeeIdAndReleasedAtIsNull(run.getId(), employeeId)) {
                log.info("PayrollEngine: skipping employee {} — on hold for run {}", employeeId, run.getId());
                continue;
            }

            // M61 / P1-02: payroll-eligibility gate. Stale APPROVED timesheets
            // belonging to TERMINATED/RETIRED/SUSPENDED employees must never
            // land in a live run — that's a financial-integrity bug. Final
            // settlement payouts are a separate, intentional path through
            // TerminationService.
            Employee emp = employees.findById(employeeId).orElse(null);
            if (emp == null) {
                log.warn("PayrollEngine: timesheet {} references missing employee {} — skipping",
                        ts.getId(), employeeId);
                continue;
            }
            if (!PAYABLE_STATUSES.contains(emp.getEmploymentStatus())) {
                log.info("PayrollEngine: skipping employee {} (status={}) — not payroll-eligible",
                        employeeId, emp.getEmploymentStatus());
                continue;
            }

            BigDecimal baseSalary = compensations.findActiveOn(employeeId, ruleDate)
                    .map(c -> c.getMonthlyBaseSalary())
                    .orElse(null);
            if (baseSalary == null) {
                // Skip employees without a compensation record — record a placeholder result for traceability.
                continue;
            }

            // M61 / P1-09: apply pro-rata multiplier for non-salaried employment
            // types. PERMANENT / FIXED_TERM / PROBATIONARY stay at 1.0 even when
            // fte_percent differs (FTE on salaried staff is informational); only
            // PART_TIME, CONTRACTOR, INTERN are scaled by fte / 100.
            EmploymentType empType = emp.getEmploymentType() != null
                    ? emp.getEmploymentType() : EmploymentType.PERMANENT;
            BigDecimal proRata = empType.proRataMultiplier(emp.getFtePercent());
            if (proRata.compareTo(BigDecimal.ONE) != 0) {
                baseSalary = baseSalary.multiply(proRata).setScale(2, RoundingMode.HALF_UP);
            }

            // OT per-day from timesheet rows.
            // M39: holiday flag from WORKED_ON_HOLIDAY anomaly (Art. 167 premium).
            // M45: weekend flag from WORKED_ON_WEEKEND anomaly (Art. 167 premium).
            //      Daily cap enforced by StatutoryCalculator via dailyOtCapHours
            //      rule field (Art. 99 — 4 h/day default).
            List<TimesheetDay> days = timesheetDays.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
            List<StatutoryCalculator.DailyOt> dailyOt = days.stream()
                    .map(d -> new StatutoryCalculator.DailyOt(
                            d.getOvertimeHours(),
                            d.getAnomalies() != null
                                    && d.getAnomalies().contains("WORKED_ON_HOLIDAY"),
                            d.getAnomalies() != null
                                    && d.getAnomalies().contains("WORKED_ON_WEEKEND")))
                    .toList();
            OvertimePay ot = calculator.overtimePay(baseSalary, dailyOt,
                    run.getJurisdiction(), ruleDate);

            BigDecimal bonusAmount = bonuses
                    .findByRunIdAndEmployeeId(run.getId(), employeeId)
                    .stream()
                    .map(PayrollBonus::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            // M41: gather active EmployeeAllowance rows for this employee
            // on the first day of the payroll period. Snapshot each row as
            // a PayrollAllowance with the taxable flag captured at calc
            // time, then split into taxable / non-taxable buckets:
            //   * taxable → added to gross before tax + contributions
            //   * non-taxable → added to net after stat deductions
            // (PRD 8.15.4 / 8.9.5 — non-taxable allowances keep the
            // employee whole without inflating the state contribution base.)
            List<EmployeeAllowance> activeAllowances = employeeAllowances
                    .findActiveForEmployeeOn(employeeId, AllowanceStatus.ACTIVE, ruleDate);
            List<PayrollAllowance> snapshots = new ArrayList<>();
            BigDecimal taxableAllowance = BigDecimal.ZERO;
            BigDecimal nonTaxableAllowance = BigDecimal.ZERO;
            LocalDate lastDayOfPeriod =
                    YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).atEndOfMonth();
            for (EmployeeAllowance ea : activeAllowances) {
                AllowanceType type = typesById.get(ea.getAllowanceTypeId());
                boolean taxable = type == null || type.isTaxable();
                PayrollAllowance snap = new PayrollAllowance();
                snap.setRunId(run.getId());
                snap.setEmployeeId(employeeId);
                snap.setEmployeeAllowanceId(ea.getId());
                snap.setAllowanceTypeId(ea.getAllowanceTypeId());
                snap.setAllowanceTypeCode(type == null ? null : type.getCode());
                snap.setAllowanceTypeName(type == null ? null : type.getName());
                snap.setAmount(ea.getAmount());
                snap.setCurrency(ea.getCurrency());
                snap.setTaxable(taxable);
                snap.setNote(ea.getNote());
                snapshots.add(allowanceSnapshots.save(snap));
                if (taxable) taxableAllowance = taxableAllowance.add(ea.getAmount());
                else nonTaxableAllowance = nonTaxableAllowance.add(ea.getAmount());
                // M220: close one-off (non-recurring) allowances after first payroll inclusion.
                if (type != null && !type.isRecurring()) {
                    ea.setStatus(AllowanceStatus.ENDED);
                    ea.setEffectiveTo(lastDayOfPeriod);
                    ea.setUpdatedBy("PAYROLL_ENGINE");
                    employeeAllowances.save(ea);
                }
            }
            taxableAllowance = taxableAllowance.setScale(2, RoundingMode.HALF_UP);
            nonTaxableAllowance = nonTaxableAllowance.setScale(2, RoundingMode.HALF_UP);
            BigDecimal allowance = taxableAllowance.add(nonTaxableAllowance);

            // ---- M349: salary component assignments (EARNING / DEDUCTION) ----
            List<SalaryComponentAssignment> assignments = componentAssignments.findActiveOn(employeeId, ruleDate);
            BigDecimal taxableComponentEarnings = BigDecimal.ZERO;
            BigDecimal nonTaxableContributableEarnings = BigDecimal.ZERO;
            BigDecimal nonTaxableNonContributableEarnings = BigDecimal.ZERO;
            BigDecimal componentDeductions = BigDecimal.ZERO;
            List<PayrollResultComponent> componentSnapshots = new ArrayList<>();

            for (SalaryComponentAssignment assignment : assignments) {
                SalaryComponent component = assignment.getComponent();

                // Resolve amount: amountOverride if set, else from component definition
                BigDecimal amount;
                if (assignment.getAmountOverride() != null) {
                    amount = assignment.getAmountOverride();
                } else if (component.getCalculationMethod() == CalculationMethod.PERCENTAGE_OF_BASE) {
                    amount = baseSalary.multiply(component.getPercentage() != null ? component.getPercentage() : BigDecimal.ZERO);
                } else {
                    // FIXED_AMOUNT or FLAT_RATE
                    amount = component.getDefaultAmount() != null ? component.getDefaultAmount() : BigDecimal.ZERO;
                }
                amount = amount.setScale(2, RoundingMode.HALF_UP);

                // Classify and accumulate
                if (component.getKind() == ComponentKind.EARNING) {
                    if (component.getIsTaxable()) {
                        taxableComponentEarnings = taxableComponentEarnings.add(amount);
                    } else if (!component.getContributionExempt()) {
                        nonTaxableContributableEarnings = nonTaxableContributableEarnings.add(amount);
                    } else {
                        nonTaxableNonContributableEarnings = nonTaxableNonContributableEarnings.add(amount);
                    }
                } else {
                    // DEDUCTION
                    componentDeductions = componentDeductions.add(amount);
                }

                // Snapshot for audit trail
                PayrollResultComponent snapshot = new PayrollResultComponent();
                snapshot.setTenantId("default");
                snapshot.setComponentId(component.getId());
                snapshot.setComponentCode(component.getCode());
                snapshot.setComponentName(component.getName());
                snapshot.setKind(component.getKind());
                snapshot.setAmount(amount);
                snapshot.setIsTaxable(component.getIsTaxable());
                snapshot.setContributionExempt(component.getContributionExempt());
                componentSnapshots.add(snapshot);
            }

            // ---- Payroll deductions (one-off, recurring, garnishment, advance recovery) ----
            List<PayrollDeduction> activeDeductions = deductions.findActiveForPeriod(
                    employeeId, run.getPeriodYear(), run.getPeriodMonth());
            BigDecimal deduction = BigDecimal.ZERO;
            for (PayrollDeduction d : activeDeductions) {
                // For ADVANCE_RECOVERY: cap the installment at the remaining balance.
                BigDecimal thisAmount = d.getAmountPerPeriod();
                if ("ADVANCE_RECOVERY".equals(d.getDeductionType()) && d.getTotalAmount() != null) {
                    BigDecimal remaining = d.getTotalAmount().subtract(d.getRecoveredAmount());
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                    thisAmount = thisAmount.min(remaining);
                }
                deduction = deduction.add(thisAmount);
            }
            deduction = deduction.setScale(2, RoundingMode.HALF_UP);

            // M327: attendance-based deductions (late / early-leave / absence).
            // Applied against baseSalary only — the policy formulas reference the
            // contractual monthly salary, not the gross (which includes OT/bonuses).
            AttendanceDeductionResult attDeduction = attendanceBridge.compute(
                    employeeId, run.getPeriodYear(), run.getPeriodMonth(), baseSalary);
            deduction = deduction.add(attDeduction.totalDeduction())
                    .setScale(2, RoundingMode.HALF_UP);

            // M349: taxable gross = base + OT + bonus + taxable allowances + taxable component earnings
            BigDecimal taxableGross = baseSalary
                    .add(ot.totalPay())
                    .add(bonusAmount)
                    .add(taxableAllowance)
                    .add(taxableComponentEarnings)
                    .subtract(deduction)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            // M349: DSMF/MMI base = taxable gross + non-taxable contributable earnings
            BigDecimal dsmfBase = taxableGross.add(nonTaxableContributableEarnings)
                    .setScale(2, RoundingMode.HALF_UP);

            IncomeTaxResult tax = calculator.incomeTax(taxableGross, run.getJurisdiction(), ruleDate);
            ContributionPair dsmf = calculator.dsmf(dsmfBase, run.getJurisdiction(), ruleDate);
            ContributionPair mmi = calculator.mmi(dsmfBase, run.getJurisdiction(), ruleDate);
            ContributionPair unempl = calculator.unemployment(dsmfBase, run.getJurisdiction(), ruleDate);

            // M349: net = taxable gross - statutory deductions + non-taxable allowances
            //       + non-taxable contributable earnings + non-taxable non-contributable earnings
            //       - component deductions (applied AFTER statutory deductions)
            BigDecimal net = taxableGross
                    .subtract(tax.tax())
                    .subtract(dsmf.employee())
                    .subtract(mmi.employee())
                    .subtract(unempl.employee())
                    .add(nonTaxableAllowance)
                    .add(nonTaxableContributableEarnings)
                    .add(nonTaxableNonContributableEarnings)
                    .subtract(componentDeductions);

            // M351/M352: apply salary advance + loan installments (post-statutory)
            BigDecimal advanceLoanDeduction = advanceLoanDeductionService.applyForEmployee(
                    employeeId, run.getPeriodYear(), run.getPeriodMonth());
            net = net.subtract(advanceLoanDeduction)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            PayrollResult r = new PayrollResult();
            r.setRunId(run.getId());
            r.setEmployeeId(employeeId);
            r.setPeriodStart(LocalDate.of(run.getPeriodYear(), run.getPeriodMonth(), 1));
            r.setPayslipNo(String.format("PS-%06d", results.nextPayslipNoSequence()));
            r.setTimesheetId(ts.getId());
            r.setBaseSalary(baseSalary);
            r.setWorkedHours(ts.getTotalWorkedHours());
            r.setExpectedMonthlyHours(ot.expectedMonthlyHours());
            r.setOvertimeHours(ot.totalHours());
            r.setOvertimePay(ot.totalPay());
            r.setBonusAmount(bonusAmount);
            r.setAllowanceAmount(allowance); // M41: combined taxable + non-taxable
            r.setDeductionAmount(deduction);
            r.setGrossAmount(taxableGross);
            r.setIncomeTax(tax.tax());
            r.setDsmfEmployee(dsmf.employee());
            r.setDsmfEmployer(dsmf.employer());
            r.setMmiEmployee(mmi.employee());
            r.setMmiEmployer(mmi.employer());
            r.setUnemplEmployee(unempl.employee());
            r.setUnemplEmployer(unempl.employer());
            r.setNetAmount(net);
            r.setCalculationDetails(buildTrace(baseSalary, ot, bonusAmount,
                    snapshots, taxableAllowance, nonTaxableAllowance,
                    attDeduction, taxableGross, tax, dsmf, mmi, unempl, net,
                    componentSnapshots));
            PayrollResult saved = results.save(r);

            // M349: persist component snapshots with result ID
            for (PayrollResultComponent snapshot : componentSnapshots) {
                snapshot.setResultId(saved.getId());
                resultComponents.save(snapshot);
            }

            // ---- Update deduction state-machine after applying ----
            for (PayrollDeduction d : activeDeductions) {
                BigDecimal thisAmount = d.getAmountPerPeriod();
                if ("ADVANCE_RECOVERY".equals(d.getDeductionType()) && d.getTotalAmount() != null) {
                    BigDecimal remaining = d.getTotalAmount().subtract(d.getRecoveredAmount());
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                    thisAmount = thisAmount.min(remaining);
                    d.setRecoveredAmount(d.getRecoveredAmount().add(thisAmount)
                            .setScale(2, RoundingMode.HALF_UP));
                    if (d.getRecoveredAmount().compareTo(d.getTotalAmount()) >= 0) {
                        d.setStatus("COMPLETED");
                    }
                } else if ("ONE_OFF".equals(d.getDeductionType())) {
                    d.setStatus("COMPLETED");
                }
                deductions.save(d);
            }

            totalGross = totalGross.add(taxableGross);
            totalIncomeTax = totalIncomeTax.add(tax.tax());
            totalDsmfEmp = totalDsmfEmp.add(dsmf.employee());
            totalDsmfEr = totalDsmfEr.add(dsmf.employer());
            totalMmiEmp = totalMmiEmp.add(mmi.employee());
            totalMmiEr = totalMmiEr.add(mmi.employer());
            totalUnEmp = totalUnEmp.add(unempl.employee());
            totalUnEr = totalUnEr.add(unempl.employer());
            totalNet = totalNet.add(net);
            totalAllowance = totalAllowance.add(allowance);
        }

        run.setTotalGross(totalGross);
        run.setTotalIncomeTax(totalIncomeTax);
        run.setTotalDsmfEmployee(totalDsmfEmp);
        run.setTotalDsmfEmployer(totalDsmfEr);
        run.setTotalMmiEmployee(totalMmiEmp);
        run.setTotalMmiEmployer(totalMmiEr);
        run.setTotalUnemplEmployee(totalUnEmp);
        run.setTotalUnemplEmployer(totalUnEr);
        run.setTotalNet(totalNet);
        run.setTotalAllowance(totalAllowance.setScale(2, RoundingMode.HALF_UP));
        run.setEmployeeCount((int) results.findByRunIdOrderByEmployeeIdAsc(run.getId()).size());
        return runs.save(run);
    }

    private String buildTrace(BigDecimal baseSalary, OvertimePay ot, BigDecimal bonus,
                               List<PayrollAllowance> allowanceLines,
                               BigDecimal taxableAllowance, BigDecimal nonTaxableAllowance,
                               AttendanceDeductionResult attDeduction,
                               BigDecimal gross, IncomeTaxResult tax, ContributionPair dsmf,
                               ContributionPair mmi, ContributionPair unempl, BigDecimal net,
                               List<PayrollResultComponent> components) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("baseSalary", baseSalary.toPlainString());
        // Overtime audit trace — split by day type so HR can verify each premium.
        // M39: holiday (Art. 167 holiday premium) vs. standard (Art. 165).
        // M45: weekend (Art. 167 weekend premium) + Art. 99 capped hours.
        BigDecimal standardHours = ot.totalHours()
                .subtract(ot.holidayHours()).subtract(ot.weekendHours());
        BigDecimal standardPay   = ot.totalPay()
                .subtract(ot.holidayPay()).subtract(ot.weekendPay());
        Map<String, Object> overtime = new LinkedHashMap<>();
        overtime.put("totalHours",           ot.totalHours().toPlainString());
        overtime.put("totalPay",             ot.totalPay().toPlainString());
        overtime.put("hourlyRate",           ot.hourlyRate().toPlainString());
        overtime.put("expectedMonthlyHours", ot.expectedMonthlyHours().toPlainString());
        overtime.put("standardHours",        standardHours.toPlainString());
        overtime.put("standardPay",          standardPay.toPlainString());
        overtime.put("holidayHours",         ot.holidayHours().toPlainString());
        overtime.put("holidayPay",           ot.holidayPay().toPlainString());
        overtime.put("weekendHours",         ot.weekendHours().toPlainString());
        overtime.put("weekendPay",           ot.weekendPay().toPlainString());
        overtime.put("cappedHours",          ot.cappedHours().toPlainString());
        trace.put("overtime", overtime);
        trace.put("bonus", bonus.toPlainString());
        // M41: allowance breakdown — line items + the taxable / non-taxable
        // split that drove the gross-vs-net math above.
        Map<String, Object> allowanceBlock = new LinkedHashMap<>();
        allowanceBlock.put("taxableTotal", taxableAllowance.toPlainString());
        allowanceBlock.put("nonTaxableTotal", nonTaxableAllowance.toPlainString());
        allowanceBlock.put("combinedTotal",
                taxableAllowance.add(nonTaxableAllowance).toPlainString());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PayrollAllowance line : allowanceLines) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", line.getAllowanceTypeCode());
            row.put("name", line.getAllowanceTypeName());
            row.put("amount", line.getAmount().toPlainString());
            row.put("taxable", line.isTaxable());
            lines.add(row);
        }
        allowanceBlock.put("lines", lines);
        trace.put("allowances", allowanceBlock);

        // M349: salary component breakdown
        List<Map<String, Object>> componentLines = new ArrayList<>();
        for (PayrollResultComponent comp : components) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", comp.getComponentCode());
            row.put("name", comp.getComponentName());
            row.put("kind", comp.getKind().name());
            row.put("amount", comp.getAmount().toPlainString());
            row.put("isTaxable", comp.getIsTaxable());
            row.put("contributionExempt", comp.getContributionExempt());
            componentLines.add(row);
        }
        trace.put("components", componentLines);

        // M327: attendance deduction breakdown
        Map<String, Object> attBlock = new LinkedHashMap<>();
        attBlock.put("policyApplied", attDeduction.policyApplied());
        attBlock.put("policyCode", attDeduction.policyCode());
        attBlock.put("totalLateMinutes", attDeduction.totalLateMinutes());
        attBlock.put("totalEarlyMinutes", attDeduction.totalEarlyMinutes());
        attBlock.put("totalAbsentDays", attDeduction.totalAbsentDays());
        attBlock.put("totalOvertimeMinutes", attDeduction.totalOvertimeMinutes());
        attBlock.put("lateDeduction", attDeduction.lateDeduction().toPlainString());
        attBlock.put("earlyLeaveDeduction", attDeduction.earlyLeaveDeduction().toPlainString());
        attBlock.put("absenceDeduction", attDeduction.absenceDeduction().toPlainString());
        attBlock.put("totalAttendanceDeduction", attDeduction.totalDeduction().toPlainString());
        trace.put("attendanceDeductions", attBlock);

        trace.put("gross", gross.toPlainString());
        trace.put("incomeTax", tax.trace());
        trace.put("dsmf", Map.of(
                "employee", dsmf.employee().toPlainString(),
                "employer", dsmf.employer().toPlainString()));
        trace.put("mmi", Map.of(
                "employee", mmi.employee().toPlainString(),
                "employer", mmi.employer().toPlainString()));
        trace.put("unemployment", Map.of(
                "employee", unempl.employee().toPlainString(),
                "employer", unempl.employer().toPlainString()));
        trace.put("net", net.toPlainString());
        try {
            return objectMapper.writeValueAsString(trace);
        } catch (JsonProcessingException ex) {
            return "{\"_serializationError\":\"" + ex.getOriginalMessage() + "\"}";
        }
    }
}
