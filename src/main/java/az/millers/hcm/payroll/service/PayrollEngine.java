package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
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
import az.millers.hcm.payroll.domain.PayrollAllowance;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollDeduction;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.repo.PayrollAllowanceRepository;
import az.millers.hcm.payroll.repo.PayrollBonusRepository;
import az.millers.hcm.payroll.repo.PayrollDeductionRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
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
    private final StatutoryCalculator calculator;
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
                          StatutoryCalculator calculator,
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
        this.calculator = calculator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PayrollRun calculate(PayrollRun run) {
        // Clear previous results + allowance snapshots so re-running
        // stays idempotent. Bonus rows are intentionally NOT wiped — they're
        // added explicitly via AddBonusRequest and should survive a recalc.
        results.deleteByRunId(run.getId());
        allowanceSnapshots.deleteByRunId(run.getId());
        results.flush();

        LocalDate ruleDate = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).atDay(1);
        List<Timesheet> approved = timesheets.findByPeriodYearAndPeriodMonthAndStatus(
                run.getPeriodYear(), run.getPeriodMonth(), TimesheetStatus.APPROVED);

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
            }
            taxableAllowance = taxableAllowance.setScale(2, RoundingMode.HALF_UP);
            nonTaxableAllowance = nonTaxableAllowance.setScale(2, RoundingMode.HALF_UP);
            BigDecimal allowance = taxableAllowance.add(nonTaxableAllowance);

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

            BigDecimal gross = baseSalary
                    .add(ot.totalPay())
                    .add(bonusAmount)
                    .add(taxableAllowance)
                    .subtract(deduction)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            IncomeTaxResult tax = calculator.incomeTax(gross, run.getJurisdiction(), ruleDate);
            ContributionPair dsmf = calculator.dsmf(gross, run.getJurisdiction(), ruleDate);
            ContributionPair mmi = calculator.mmi(gross, run.getJurisdiction(), ruleDate);
            ContributionPair unempl = calculator.unemployment(gross, run.getJurisdiction(), ruleDate);

            BigDecimal net = gross
                    .subtract(tax.tax())
                    .subtract(dsmf.employee())
                    .subtract(mmi.employee())
                    .subtract(unempl.employee())
                    .add(nonTaxableAllowance)
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
            r.setGrossAmount(gross);
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
                    gross, tax, dsmf, mmi, unempl, net));
            results.save(r);

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

            totalGross = totalGross.add(gross);
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
                               BigDecimal gross, IncomeTaxResult tax, ContributionPair dsmf,
                               ContributionPair mmi, ContributionPair unempl, BigDecimal net) {
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
