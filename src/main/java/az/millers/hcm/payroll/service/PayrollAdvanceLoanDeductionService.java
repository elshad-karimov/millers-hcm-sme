package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.payroll.domain.PayrollDeduction;
import az.millers.hcm.payroll.domain.PayrollLoan;
import az.millers.hcm.payroll.domain.SalaryAdvance;
import az.millers.hcm.payroll.repo.PayrollDeductionRepository;
import az.millers.hcm.payroll.repo.PayrollLoanRepository;
import az.millers.hcm.payroll.repo.SalaryAdvanceRepository;

/**
 * M351/M352 — Hook for PayrollEngine to apply salary advance + loan installments.
 *
 * <p>Two phases per call so the result is recalculation-safe:
 * <ol>
 *   <li><b>Ensure</b> — create the deduction row for each due advance/loan exactly once
 *       (per-advance for advances, per-(loan, period) for loan installments) and run the
 *       state mutations (mark advance DEDUCTED, reduce the loan balance) only on first
 *       creation.</li>
 *   <li><b>Sum</b> — derive the post-statutory deduction from the persisted rows for the
 *       period. Recalculating an already-calculated run re-reads the same rows, so net pay
 *       is deterministic instead of dropping to zero on a second calculation.</li>
 * </ol>
 */
@Service
public class PayrollAdvanceLoanDeductionService {

    private static final Logger log = LoggerFactory.getLogger(PayrollAdvanceLoanDeductionService.class);

    private static final List<String> ADVANCE_LOAN_TYPES = List.of("SALARY_ADVANCE", "LOAN_INSTALLMENT");

    private final SalaryAdvanceRepository advanceRepo;
    private final PayrollLoanRepository loanRepo;
    private final PayrollDeductionRepository deductionRepo;
    private final SalaryAdvanceService advanceService;
    private final PayrollLoanService loanService;

    public PayrollAdvanceLoanDeductionService(SalaryAdvanceRepository advanceRepo,
                                              PayrollLoanRepository loanRepo,
                                              PayrollDeductionRepository deductionRepo,
                                              SalaryAdvanceService advanceService,
                                              PayrollLoanService loanService) {
        this.advanceRepo = advanceRepo;
        this.loanRepo = loanRepo;
        this.deductionRepo = deductionRepo;
        this.advanceService = advanceService;
        this.loanService = loanService;
    }

    /**
     * Apply advance + loan deductions for one employee in the given period and return the
     * total to subtract from net pay AFTER statutory deductions.
     *
     * <p>Idempotent and recalculation-safe: the row is created (and the advance/loan state
     * mutated) only on the first calculation; the returned amount is always re-derived from
     * the persisted rows for the period, so recalculating the run yields the same net.
     */
    @Transactional
    public BigDecimal applyForEmployee(UUID employeeId, int year, int month) {
        // Phase 1 — ensure the deduction rows exist (mutations run once, on first creation).

        // Advances: an advance is recovered exactly once, in its repayment period.
        for (SalaryAdvance advance : advanceRepo.findApprovedForPeriod(employeeId, year, month)) {
            if (deductionRepo.findBySourceAdvanceId(advance.getId()).isPresent()) {
                continue; // already recorded — leave the advance as DEDUCTED
            }
            PayrollDeduction deduction = newRow(employeeId, year, month, "SALARY_ADVANCE",
                    "Salary advance repayment: " + advance.getReason(), advance.getApprovedAmount());
            deduction.setSourceAdvanceId(advance.getId());
            deductionRepo.save(deduction);
            advanceService.markDeducted(advance.getId());
            log.info("Created SALARY_ADVANCE deduction for employee {} advance {} amount {}",
                    employeeId, advance.getId(), advance.getApprovedAmount());
        }

        // Loans: one installment per (loan, period); idempotency is keyed by both so a
        // multi-month loan keeps deducting in subsequent periods.
        for (PayrollLoan loan : loanRepo.findActiveForPeriod(employeeId, year, month)) {
            if (deductionRepo.findBySourceLoanIdAndStartPeriodYearAndStartPeriodMonth(
                    loan.getId(), year, month).isPresent()) {
                continue; // installment for this period already recorded
            }
            BigDecimal installment = loan.getMonthlyInstallment().min(loan.getOutstandingBalance())
                    .setScale(2, RoundingMode.HALF_UP);
            PayrollDeduction deduction = newRow(employeeId, year, month, "LOAN_INSTALLMENT",
                    "Loan installment: " + loan.getDescription(), installment);
            deduction.setSourceLoanId(loan.getId());
            deductionRepo.save(deduction);
            loanService.applyInstallment(loan.getId(), installment);
            log.info("Created LOAN_INSTALLMENT deduction for employee {} loan {} installment {}",
                    employeeId, loan.getId(), installment);
        }

        // Phase 2 — sum the persisted advance/loan rows for this period (recalc-safe).
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollDeduction d : deductionRepo
                .findByEmployeeIdAndStartPeriodYearAndStartPeriodMonthAndDeductionTypeIn(
                        employeeId, year, month, ADVANCE_LOAN_TYPES)) {
            total = total.add(d.getAmountPerPeriod());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private PayrollDeduction newRow(UUID employeeId, int year, int month, String type,
                                    String description, BigDecimal amount) {
        PayrollDeduction d = new PayrollDeduction();
        d.setEmployeeId(employeeId);
        d.setDeductionType(type);
        d.setDescription(description);
        d.setAmountPerPeriod(amount);
        d.setStartPeriodYear(year);
        d.setStartPeriodMonth(month);
        // COMPLETED so the engine's ACTIVE-only standing-deduction loop never re-applies it;
        // this hook owns the per-period application of advance/loan recovery.
        d.setStatus("COMPLETED");
        d.setCreatedBy("PAYROLL_ENGINE");
        return d;
    }
}
