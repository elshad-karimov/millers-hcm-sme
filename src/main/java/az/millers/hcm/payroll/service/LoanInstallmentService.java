package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.domain.LoanInstallmentSchedule;
import az.millers.hcm.payroll.domain.LoanInstallmentStatus;
import az.millers.hcm.payroll.domain.LoanRequest;
import az.millers.hcm.payroll.repo.LoanInstallmentScheduleRepository;
import az.millers.hcm.payroll.repo.LoanRequestRepository;

/**
 * M462 — Loan installment schedule generation and management.
 */
@Service
public class LoanInstallmentService {
    private static final String TENANT = "default";

    private final LoanInstallmentScheduleRepository repository;
    private final LoanRequestRepository loanRequestRepo;

    public LoanInstallmentService(LoanInstallmentScheduleRepository repository, LoanRequestRepository loanRequestRepo) {
        this.repository = repository;
        this.loanRequestRepo = loanRequestRepo;
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
            installment.setTenantId(TENANT);
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
}
