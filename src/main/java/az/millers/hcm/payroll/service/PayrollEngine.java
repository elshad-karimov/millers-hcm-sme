package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import az.millers.hcm.payroll.domain.PayrollAllowance;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.repo.PayrollAllowanceRepository;
import az.millers.hcm.payroll.repo.PayrollBonusRepository;
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

    private final PayrollRunRepository runs;
    private final PayrollResultRepository results;
    private final PayrollBonusRepository bonuses;
    private final PayrollAllowanceRepository allowanceSnapshots;
    private final TimesheetRepository timesheets;
    private final TimesheetDayRepository timesheetDays;
    private final EmployeeCompensationRepository compensations;
    private final EmployeeAllowanceRepository employeeAllowances;
    private final AllowanceTypeRepository allowanceTypes;
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
            BigDecimal baseSalary = compensations.findActiveOn(employeeId, ruleDate)
                    .map(c -> c.getMonthlyBaseSalary())
                    .orElse(null);
            if (baseSalary == null) {
                // Skip employees without a compensation record — record a placeholder result for traceability.
                continue;
            }

            // OT per-day from timesheet rows. M39: each day also carries
            // a holiday flag pulled from the WORKED_ON_HOLIDAY anomaly
            // that TimesheetGenerator emits (M38) — the calculator
            // applies holidayMultiplier (Article 167) when set.
            List<TimesheetDay> days = timesheetDays.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
            List<StatutoryCalculator.DailyOt> dailyOt = days.stream()
                    .map(d -> new StatutoryCalculator.DailyOt(
                            d.getOvertimeHours(),
                            d.getAnomalies() != null
                                    && d.getAnomalies().contains("WORKED_ON_HOLIDAY")))
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

            BigDecimal deduction = BigDecimal.ZERO;
            BigDecimal gross = baseSalary
                    .add(ot.totalPay())
                    .add(bonusAmount)
                    .add(taxableAllowance)
                    .subtract(deduction)
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
        // M39: split the overtime trace so HR can audit what was paid
        // at standard rate (Article 165) vs. holiday rate (Article 167).
        BigDecimal standardHours = ot.totalHours().subtract(ot.holidayHours());
        BigDecimal standardPay = ot.totalPay().subtract(ot.holidayPay());
        Map<String, Object> overtime = new LinkedHashMap<>();
        overtime.put("totalHours", ot.totalHours().toPlainString());
        overtime.put("totalPay", ot.totalPay().toPlainString());
        overtime.put("hourlyRate", ot.hourlyRate().toPlainString());
        overtime.put("expectedMonthlyHours", ot.expectedMonthlyHours().toPlainString());
        overtime.put("standardHours", standardHours.toPlainString());
        overtime.put("standardPay", standardPay.toPlainString());
        overtime.put("holidayHours", ot.holidayHours().toPlainString());
        overtime.put("holidayPay", ot.holidayPay().toPlainString());
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
