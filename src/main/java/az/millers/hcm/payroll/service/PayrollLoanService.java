package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.domain.PayrollLoan;
import az.millers.hcm.payroll.domain.PayrollLoanStatus;
import az.millers.hcm.payroll.repo.PayrollLoanRepository;

/**
 * M352 — Payroll loan management.
 * Loans are repaid via fixed monthly installments until outstanding balance reaches zero.
 */
@Service
public class PayrollLoanService {

    private static final String MODULE = "payroll";
    private static final String ENTITY = "PayrollLoan";

    private final PayrollLoanRepository loanRepo;
    private final AuditService audit;

    public PayrollLoanService(PayrollLoanRepository loanRepo, AuditService audit) {
        this.loanRepo = loanRepo;
        this.audit = audit;
    }

    @Transactional
    public PayrollLoan create(UUID employeeId, BigDecimal principal, BigDecimal monthlyInstallment,
                              int startYear, int startMonth, String description, String approvedBy) {
        if (monthlyInstallment.compareTo(BigDecimal.ONE) < 0) {
            throw new BadRequestException("Monthly installment must be at least 1");
        }
        if (monthlyInstallment.compareTo(principal) > 0) {
            throw new BadRequestException("Monthly installment cannot exceed principal");
        }

        int termMonths = principal.divide(monthlyInstallment, 0, RoundingMode.UP).intValue();

        PayrollLoan loan = new PayrollLoan();
        loan.setEmployeeId(employeeId);
        loan.setPrincipalAmount(principal);
        loan.setMonthlyInstallment(monthlyInstallment);
        loan.setOutstandingBalance(principal);
        loan.setAmountRepaid(BigDecimal.ZERO);
        loan.setStartDeductionYear(startYear);
        loan.setStartDeductionMonth(startMonth);
        loan.setDescription(description);
        loan.setStatus(PayrollLoanStatus.ACTIVE);
        loan.setApprovedBy(approvedBy);
        loan.setApprovedAt(OffsetDateTime.now());
        PayrollLoan saved = loanRepo.save(loan);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null,
                Map.of("principal", principal.toPlainString(),
                        "monthlyInstallment", monthlyInstallment.toPlainString(),
                        "termMonths", termMonths));
        return saved;
    }

    @Transactional
    public PayrollLoan cancel(UUID loanId) {
        PayrollLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll loan not found"));

        if (loan.getAmountRepaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Cannot cancel a loan with partial repayments");
        }

        loan.setStatus(PayrollLoanStatus.CANCELLED);
        PayrollLoan saved = loanRepo.save(loan);

        audit.record(MODULE, ENTITY, loanId.toString(), "CANCELLED", null, null);
        return saved;
    }

    @Transactional
    public PayrollLoan writeOff(UUID loanId, String reason) {
        PayrollLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll loan not found"));

        loan.setStatus(PayrollLoanStatus.WRITTEN_OFF);
        PayrollLoan saved = loanRepo.save(loan);

        audit.record(MODULE, ENTITY, loanId.toString(), "WRITTEN_OFF", null, Map.of("reason", reason));
        return saved;
    }

    @Transactional
    public void applyInstallment(UUID loanId, BigDecimal amount) {
        PayrollLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll loan not found"));

        BigDecimal newRepaid = loan.getAmountRepaid().add(amount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newBalance = loan.getOutstandingBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP);

        loan.setAmountRepaid(newRepaid);
        loan.setOutstandingBalance(newBalance.max(BigDecimal.ZERO));

        if (loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(PayrollLoanStatus.FULLY_REPAID);
            loanRepo.save(loan);
            audit.record(MODULE, ENTITY, loanId.toString(), "STATUS_CHANGED", null,
                    Map.of("status", "FULLY_REPAID"));
        } else {
            loanRepo.save(loan);
        }
    }

    @Transactional(readOnly = true)
    public List<PayrollLoan> findByEmployeeId(UUID employeeId) {
        return loanRepo.findByEmployeeIdAndStatus(employeeId, PayrollLoanStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<PayrollLoan> findAll() {
        return loanRepo.findAll();
    }

    @Transactional(readOnly = true)
    public PayrollLoan get(UUID loanId) {
        return loanRepo.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll loan not found"));
    }
}
