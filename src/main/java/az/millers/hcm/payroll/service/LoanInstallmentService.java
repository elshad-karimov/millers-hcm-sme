package az.millers.hcm.payroll.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.domain.LoanInstallmentSchedule;
import az.millers.hcm.payroll.domain.LoanInstallmentStatus;
import az.millers.hcm.payroll.domain.LoanRequest;
import az.millers.hcm.payroll.repo.LoanInstallmentScheduleRepository;
import az.millers.hcm.payroll.repo.LoanRequestRepository;

/**
 * M462 — Loan installment schedule generation and management.
 * M463 — Early settlement and reschedule.
 */
@Service
public class LoanInstallmentService {
    private static final String MODULE = "payroll";
    private static final String ENTITY = "LoanInstallmentSchedule";

    private final LoanInstallmentScheduleRepository repository;
    private final LoanRequestRepository loanRequestRepo;
    private final PayrollLoanService payrollLoanService;
    private final AuditService audit;

    public LoanInstallmentService(LoanInstallmentScheduleRepository repository,
                                  LoanRequestRepository loanRequestRepo,
                                  PayrollLoanService payrollLoanService,
                                  AuditService audit) {
        this.repository = repository;
        this.loanRequestRepo = loanRequestRepo;
        this.payrollLoanService = payrollLoanService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<LoanInstallmentSchedule> getSchedule(UUID loanRequestId) {
        return repository.findByLoanRequestIdOrderByInstallmentNumber(loanRequestId);
    }

    @Transactional
    public List<LoanInstallmentSchedule> generateSchedule(UUID loanRequestId, UUID payrollLoanId) {
        LoanRequest request = loanRequestRepo.findById(loanRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found"));

        // Clear existing schedule (regeneration on reschedule)
        List<LoanInstallmentSchedule> existing = repository.findByLoanRequestIdOrderByInstallmentNumber(loanRequestId);
        repository.deleteAll(existing);

        BigDecimal principal = request.getRequestedAmount();
        int months = request.getRequestedMonths();
        BigDecimal monthlyInstallment = principal.divide(new BigDecimal(months), 2, RoundingMode.HALF_UP);

        LocalDate now = LocalDate.now();
        int startYear = now.getYear();
        int startMonth = now.getMonthValue();

        List<LoanInstallmentSchedule> schedule = new ArrayList<>();
        BigDecimal remainingBalance = principal;

        for (int i = 1; i <= months; i++) {
            int dueMonth = (startMonth + i - 1) % 12 + 1;
            int dueYear = startYear + (startMonth + i - 1) / 12;

            BigDecimal installmentAmt = (i == months) ? remainingBalance : monthlyInstallment;

            LoanInstallmentSchedule installment = new LoanInstallmentSchedule();
            installment.setTenantId(TenantContext.current());
            installment.setLoanRequestId(loanRequestId);
            installment.setPayrollLoanId(payrollLoanId);
            installment.setInstallmentNumber(i);
            installment.setDueYear(dueYear);
            installment.setDueMonth(dueMonth);
            installment.setInstallmentAmount(installmentAmt);
            installment.setPrincipalAmount(installmentAmt);
            installment.setInterestAmount(BigDecimal.ZERO);
            remainingBalance = remainingBalance.subtract(installmentAmt);
            installment.setRemainingBalance(remainingBalance.max(BigDecimal.ZERO));
            installment.setStatus(LoanInstallmentStatus.PENDING);

            schedule.add(repository.save(installment));
        }

        return schedule;
    }

    @Transactional
    public void markPaid(UUID installmentId, BigDecimal paidAmount) {
        LoanInstallmentSchedule installment = repository.findById(installmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Installment not found"));
        installment.setPaidAmount(paidAmount);
        installment.setStatus(paidAmount.compareTo(installment.getInstallmentAmount()) >= 0
                ? LoanInstallmentStatus.PAID : LoanInstallmentStatus.PARTIALLY_PAID);
        installment.setPaidAt(java.time.OffsetDateTime.now());
        repository.save(installment);
    }

    /**
     * M463 — Early settlement (full or partial).
     * Reduces outstanding balance via PayrollLoanService.applyInstallment,
     * regenerates remaining schedule if partial.
     */
    @Transactional
    public void settle(UUID loanRequestId, BigDecimal amount) {
        LoanRequest request = loanRequestRepo.findById(loanRequestId)
                .filter(r -> TenantContext.current().equals(r.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found"));

        if (request.getPayrollLoanId() == null) {
            throw new BadRequestException("Loan request has no associated payroll loan");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Settlement amount must be positive");
        }

        // Apply settlement via PayrollLoanService
        payrollLoanService.applyInstallment(request.getPayrollLoanId(), amount);

        // Mark existing schedule installments as settled or partially settled
        List<LoanInstallmentSchedule> schedule = repository.findByLoanRequestIdOrderByInstallmentNumber(loanRequestId);
        BigDecimal remainingSettlement = amount;

        for (LoanInstallmentSchedule installment : schedule) {
            if (installment.getStatus() == LoanInstallmentStatus.PAID) {
                continue;
            }

            BigDecimal outstanding = installment.getInstallmentAmount()
                    .subtract(installment.getPaidAmount() != null ? installment.getPaidAmount() : BigDecimal.ZERO);

            if (remainingSettlement.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            if (remainingSettlement.compareTo(outstanding) >= 0) {
                // Fully settle this installment
                installment.setPaidAmount(installment.getInstallmentAmount());
                installment.setStatus(LoanInstallmentStatus.PAID);
                installment.setPaidAt(java.time.OffsetDateTime.now());
                remainingSettlement = remainingSettlement.subtract(outstanding);
            } else {
                // Partially settle this installment
                BigDecimal newPaid = (installment.getPaidAmount() != null ? installment.getPaidAmount() : BigDecimal.ZERO)
                        .add(remainingSettlement);
                installment.setPaidAmount(newPaid);
                installment.setStatus(LoanInstallmentStatus.PARTIALLY_PAID);
                remainingSettlement = BigDecimal.ZERO;
            }
            repository.save(installment);
        }

        audit.record(MODULE, ENTITY, loanRequestId.toString(), "SETTLE",
                null, Map.of("amount", amount.toPlainString()));
    }

    /**
     * M463 — Reschedule loan with new monthly installment.
     * Recomputes schedule based on outstanding balance and new installment amount.
     */
    @Transactional
    public List<LoanInstallmentSchedule> reschedule(UUID loanRequestId, BigDecimal newMonthlyInstallment) {
        LoanRequest request = loanRequestRepo.findById(loanRequestId)
                .filter(r -> TenantContext.current().equals(r.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found"));

        if (request.getPayrollLoanId() == null) {
            throw new BadRequestException("Loan request has no associated payroll loan");
        }

        if (newMonthlyInstallment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("New monthly installment must be positive");
        }

        // Get current loan state
        var payrollLoan = payrollLoanService.get(request.getPayrollLoanId());
        BigDecimal outstandingBalance = payrollLoan.getOutstandingBalance();

        if (outstandingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Loan is fully repaid, cannot reschedule");
        }

        if (newMonthlyInstallment.compareTo(outstandingBalance) > 0) {
            throw new BadRequestException("New monthly installment cannot exceed outstanding balance");
        }

        // Clear existing unpaid schedule
        List<LoanInstallmentSchedule> existing = repository.findByLoanRequestIdOrderByInstallmentNumber(loanRequestId);
        for (LoanInstallmentSchedule installment : existing) {
            if (installment.getStatus() != LoanInstallmentStatus.PAID) {
                repository.delete(installment);
            }
        }

        // Calculate new schedule
        int newMonths = outstandingBalance.divide(newMonthlyInstallment, 0, RoundingMode.UP).intValue();
        LocalDate now = LocalDate.now();
        int startYear = now.getYear();
        int startMonth = now.getMonthValue();

        // Determine starting installment number (after last paid)
        int lastPaidNumber = existing.stream()
                .filter(i -> i.getStatus() == LoanInstallmentStatus.PAID)
                .mapToInt(LoanInstallmentSchedule::getInstallmentNumber)
                .max()
                .orElse(0);

        List<LoanInstallmentSchedule> newSchedule = new ArrayList<>();
        BigDecimal remainingBalance = outstandingBalance;

        for (int i = 1; i <= newMonths; i++) {
            int dueMonth = (startMonth + i - 1) % 12 + 1;
            int dueYear = startYear + (startMonth + i - 1) / 12;

            BigDecimal installmentAmt = (i == newMonths) ? remainingBalance : newMonthlyInstallment;

            LoanInstallmentSchedule installment = new LoanInstallmentSchedule();
            installment.setTenantId(TenantContext.current());
            installment.setLoanRequestId(loanRequestId);
            installment.setPayrollLoanId(request.getPayrollLoanId());
            installment.setInstallmentNumber(lastPaidNumber + i);
            installment.setDueYear(dueYear);
            installment.setDueMonth(dueMonth);
            installment.setInstallmentAmount(installmentAmt);
            installment.setPrincipalAmount(installmentAmt);
            installment.setInterestAmount(BigDecimal.ZERO);
            remainingBalance = remainingBalance.subtract(installmentAmt);
            installment.setRemainingBalance(remainingBalance.max(BigDecimal.ZERO));
            installment.setStatus(LoanInstallmentStatus.PENDING);

            newSchedule.add(repository.save(installment));
        }

        audit.record(MODULE, ENTITY, loanRequestId.toString(), "RESCHEDULE",
                null, Map.of("newMonthlyInstallment", newMonthlyInstallment.toPlainString(),
                        "newMonths", newMonths));

        return newSchedule;
    }
}
