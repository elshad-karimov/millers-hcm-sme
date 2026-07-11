package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.domain.RunType;
import az.millers.hcm.payroll.repo.PayrollLoanRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.payroll.service.StatutoryCalculator.ContributionPair;
import az.millers.hcm.payroll.service.StatutoryCalculator.IncomeTaxResult;
import az.millers.hcm.common.BusinessNumbers;

/**
 * Creates a {@code FINAL_SETTLEMENT} payroll run when a termination is
 * processed (M180 / PRD §8.11.4).
 *
 * <p>The run is pre-approved (no workflow) because it is a direct consequence
 * of an already-approved termination. Statutory deductions are applied to the
 * gross settlement amount using the same {@link StatutoryCalculator} that the
 * regular payroll engine uses. If no statutory rule is found (e.g., missing
 * seed data in a test environment), deductions are recorded as zero and a
 * warning is logged rather than blocking the termination.
 */
@Service
public class FinalSettlementService {

    private static final Logger log = LoggerFactory.getLogger(FinalSettlementService.class);
    private static final RunType RUN_TYPE = RunType.FINAL_SETTLEMENT;
    private static final String MODULE   = "PAYROLL";
    private static final String ENTITY   = "PayrollRun";

    private final PayrollRunRepository runs;
    private final PayrollResultRepository results;
    private final StatutoryCalculator calculator;
    private final AuditService audit;
    private final SalaryAdvanceService advanceService;
    private final PayrollLoanRepository loanRepo;
    private final ObjectMapper objectMapper;

    public FinalSettlementService(PayrollRunRepository runs,
                                  PayrollResultRepository results,
                                  StatutoryCalculator calculator,
                                  AuditService audit,
                                  SalaryAdvanceService advanceService,
                                  PayrollLoanRepository loanRepo,
                                  ObjectMapper objectMapper) {
        this.runs = runs;
        this.results = results;
        this.calculator = calculator;
        this.audit = audit;
        this.advanceService = advanceService;
        this.loanRepo = loanRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a FINAL_SETTLEMENT payroll run for the given termination and
     * returns the saved {@link PayrollRun}.
     *
     * @param terminationId  UUID of the termination request (used as a natural key
     *                       to ensure idempotency — re-calling does nothing if a run
     *                       already exists)
     * @param employeeId     terminated employee
     * @param effectiveDate  termination effective date (determines the payroll period)
     * @param grossSettlement  pre-tax settlement amount (unusedLeavePayout + severance)
     * @param unusedLeavePayout  breakdown: leave compensation component
     * @param severance          breakdown: severance component
     * @param currency           e.g. "AZN"
     * @param jurisdiction       e.g. "AZ"
     * @param terminationNo      used in the run_no and note
     */
    @Transactional
    public PayrollRun createRun(UUID terminationId,
                                UUID employeeId,
                                LocalDate effectiveDate,
                                BigDecimal grossSettlement,
                                BigDecimal unusedLeavePayout,
                                BigDecimal severance,
                                String currency,
                                String jurisdiction,
                                String terminationNo) {

        // Idempotency: if this termination already has a payroll run, return it.
        return runs.findByTerminationId(terminationId).orElseGet(() ->
                buildAndSave(terminationId, employeeId, effectiveDate,
                        grossSettlement, unusedLeavePayout, severance,
                        currency, jurisdiction, terminationNo));
    }

    // ---------- Internals ----------

    private PayrollRun buildAndSave(UUID terminationId,
                                    UUID employeeId,
                                    LocalDate effectiveDate,
                                    BigDecimal grossSettlement,
                                    BigDecimal unusedLeavePayout,
                                    BigDecimal severance,
                                    String currency,
                                    String jurisdiction,
                                    String terminationNo) {

        int periodYear  = effectiveDate.getYear();
        int periodMonth = effectiveDate.getMonthValue();
        LocalDate periodStart = LocalDate.of(periodYear, periodMonth, 1);

        // ---- Statutory deductions on gross settlement ----
        Map<String, Object> calcDetail = new LinkedHashMap<>();
        calcDetail.put("grossSettlement", grossSettlement.toPlainString());
        calcDetail.put("unusedLeavePayout", unusedLeavePayout.toPlainString());
        calcDetail.put("severance", severance.toPlainString());

        BigDecimal incomeTax      = BigDecimal.ZERO;
        BigDecimal dsmfEmp        = BigDecimal.ZERO;
        BigDecimal dsmfEr         = BigDecimal.ZERO;
        BigDecimal mmiEmp         = BigDecimal.ZERO;
        BigDecimal mmiEr          = BigDecimal.ZERO;
        BigDecimal unemplEmp      = BigDecimal.ZERO;
        BigDecimal unemplEr       = BigDecimal.ZERO;

        try {
            IncomeTaxResult taxResult = calculator.incomeTax(grossSettlement, jurisdiction, effectiveDate);
            incomeTax = taxResult.tax();
            calcDetail.put("incomeTax", taxResult.trace());
        } catch (Exception ex) {
            log.warn("Final settlement: income tax rule not found for {}/{}: {}",
                    terminationNo, jurisdiction, ex.getMessage());
            calcDetail.put("incomeTaxWarning", ex.getMessage());
        }
        try {
            ContributionPair dsmf = calculator.dsmf(grossSettlement, jurisdiction, effectiveDate);
            dsmfEmp = dsmf.employee();
            dsmfEr  = dsmf.employer();
            calcDetail.put("dsmf", Map.of("employee", dsmf.employee(), "employer", dsmf.employer()));
        } catch (Exception ex) {
            log.warn("Final settlement: DSMF rule not found: {}", ex.getMessage());
        }
        try {
            ContributionPair mmi = calculator.mmi(grossSettlement, jurisdiction, effectiveDate);
            mmiEmp = mmi.employee();
            mmiEr  = mmi.employer();
            calcDetail.put("mmi", Map.of("employee", mmi.employee(), "employer", mmi.employer()));
        } catch (Exception ex) {
            log.warn("Final settlement: MMI rule not found: {}", ex.getMessage());
        }
        try {
            ContributionPair unempl = calculator.unemployment(grossSettlement, jurisdiction, effectiveDate);
            unemplEmp = unempl.employee();
            unemplEr  = unempl.employer();
            calcDetail.put("unemployment", Map.of("employee", unempl.employee(), "employer", unempl.employer()));
        } catch (Exception ex) {
            log.warn("Final settlement: unemployment rule not found: {}", ex.getMessage());
        }

        BigDecimal totalDeductions = incomeTax.add(dsmfEmp).add(mmiEmp).add(unemplEmp)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossSettlement.subtract(totalDeductions)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // M351/M352: recover outstanding advances + loans from final payout
        BigDecimal advanceDeduction = advanceService.outstandingForEmployee(employeeId).stream()
                .map(advance -> advance.getApprovedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal loanDeduction = loanRepo.findActiveByEmployee(employeeId).stream()
                .map(loan -> loan.getOutstandingBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAdvanceLoan = advanceDeduction.add(loanDeduction).setScale(2, RoundingMode.HALF_UP);

        BigDecimal payoutBeforeFloor = netAmount;
        netAmount = netAmount.subtract(totalAdvanceLoan).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        if (totalAdvanceLoan.compareTo(payoutBeforeFloor) > 0) {
            BigDecimal writeOffAmount = totalAdvanceLoan.subtract(payoutBeforeFloor).setScale(2, RoundingMode.HALF_UP);
            calcDetail.put("advanceLoanWriteOff", writeOffAmount.toPlainString());
            audit.record(MODULE, "FinalSettlementWriteOff", employeeId.toString(), "WRITE_OFF", null,
                    Map.of("writeOffAmount", writeOffAmount.toPlainString(), "requiresHrAcknowledgement", true));
        }

        // Mark advances as DEDUCTED and loans as FULLY_REPAID or WRITTEN_OFF
        for (var advance : advanceService.outstandingForEmployee(employeeId)) {
            advanceService.markDeducted(advance.getId());
        }
        for (var loan : loanRepo.findActiveByEmployee(employeeId)) {
            if (loan.getOutstandingBalance().compareTo(payoutBeforeFloor) <= 0) {
                loan.setStatus(az.millers.hcm.payroll.domain.PayrollLoanStatus.FULLY_REPAID);
                loanRepo.save(loan);
            } else {
                loan.setStatus(az.millers.hcm.payroll.domain.PayrollLoanStatus.WRITTEN_OFF);
                loanRepo.save(loan);
            }
        }

        calcDetail.put("advanceDeduction", advanceDeduction.toPlainString());
        calcDetail.put("loanDeduction", loanDeduction.toPlainString());
        calcDetail.put("totalDeductions", totalDeductions.toPlainString());
        calcDetail.put("netAmount", netAmount.toPlainString());

        // ---- Create run ----
        PayrollRun run = new PayrollRun();
        run.setRunNo(String.format("FS-%s", terminationNo));
        run.setRunType(RUN_TYPE);
        run.setTerminationId(terminationId);
        run.setPeriodYear(periodYear);
        run.setPeriodMonth(periodMonth);
        run.setJurisdiction(jurisdiction != null ? jurisdiction : "AZ");
        run.setCurrency(currency != null ? currency : "AZN");
        run.setStatus(PayrollRunStatus.APPROVED);
        run.setEmployeeCount(1);
        run.setTotalGross(grossSettlement);
        run.setTotalIncomeTax(incomeTax);
        run.setTotalDsmfEmployee(dsmfEmp);
        run.setTotalDsmfEmployer(dsmfEr);
        run.setTotalMmiEmployee(mmiEmp);
        run.setTotalMmiEmployer(mmiEr);
        run.setTotalUnemplEmployee(unemplEmp);
        run.setTotalUnemplEmployer(unemplEr);
        run.setTotalNet(netAmount);
        run.setTotalAllowance(unusedLeavePayout);
        run.setCalculatedAt(OffsetDateTime.now());
        run.setCalculatedBy("system");
        run.setApprovedAt(OffsetDateTime.now());
        run.setApprovedBy("system");
        run.setCreatedBy("system");
        PayrollRun savedRun = runs.save(run);

        // ---- Create single result row ----
        String calcDetailsJson;
        try {
            calcDetailsJson = objectMapper.writeValueAsString(calcDetail);
        } catch (Exception ex) {
            calcDetailsJson = "{}";
        }

        PayrollResult result = new PayrollResult();
        result.setRunId(savedRun.getId());
        result.setEmployeeId(employeeId);
        result.setPayslipNo(BusinessNumbers.format("FS", 7, results.nextPayslipNoSequence()));
        result.setPeriodStart(periodStart);
        result.setAllowanceAmount(unusedLeavePayout);
        result.setBonusAmount(severance);
        result.setGrossAmount(grossSettlement);
        result.setIncomeTax(incomeTax);
        result.setDsmfEmployee(dsmfEmp);
        result.setDsmfEmployer(dsmfEr);
        result.setMmiEmployee(mmiEmp);
        result.setMmiEmployer(mmiEr);
        result.setUnemplEmployee(unemplEmp);
        result.setUnemplEmployer(unemplEr);
        result.setNetAmount(netAmount);
        result.setCalculationDetails(calcDetailsJson);
        result.setNote("Final settlement for termination " + terminationNo);
        results.save(result);

        audit.record(MODULE, ENTITY, savedRun.getId().toString(),
                "FINAL_SETTLEMENT_CREATED", null,
                Map.of("terminationId", terminationId.toString(),
                        "terminationNo", terminationNo,
                        "gross", grossSettlement.toPlainString(),
                        "net", netAmount.toPlainString()));

        log.info("Final settlement run {} created for termination {} — gross={} net={}",
                savedRun.getRunNo(), terminationNo, grossSettlement, netAmount);
        return savedRun;
    }
}
