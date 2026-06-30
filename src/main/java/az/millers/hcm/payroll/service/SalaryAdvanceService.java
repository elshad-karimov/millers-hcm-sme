package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.domain.SalaryAdvance;
import az.millers.hcm.payroll.domain.SalaryAdvanceStatus;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.repo.SalaryAdvanceRepository;

/**
 * M351 — Salary advance management.
 * Employees request advances (max 50% of monthly base salary).
 * Approved advances create a SALARY_ADVANCE deduction in the specified repayment period.
 */
@Service
public class SalaryAdvanceService {

    private static final String MODULE = "payroll";
    private static final String ENTITY = "SalaryAdvance";

    private final SalaryAdvanceRepository advanceRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final AuditService audit;

    public SalaryAdvanceService(SalaryAdvanceRepository advanceRepo,
                                EmployeeCompensationRepository compensationRepo,
                                AuditService audit) {
        this.advanceRepo = advanceRepo;
        this.compensationRepo = compensationRepo;
        this.audit = audit;
    }

    @Transactional
    public SalaryAdvance request(UUID employeeId, BigDecimal requestedAmount, String reason, String requestedBy) {
        // Ceiling: 50% of monthly base salary
        EmployeeCompensation comp = compensationRepo.findActiveOn(employeeId, LocalDate.now())
                .orElseThrow(() -> new BadRequestException("No active compensation found for employee"));
        BigDecimal ceiling = comp.getMonthlyBaseSalary().multiply(new BigDecimal("0.50"));
        if (requestedAmount.compareTo(ceiling) > 0) {
            throw new BadRequestException("ADVANCE_LIMIT_EXCEEDED");
        }

        // One-at-a-time: no PENDING or APPROVED advance exists
        if (advanceRepo.existsByEmployeeIdAndStatusIn(employeeId,
                List.of(SalaryAdvanceStatus.PENDING, SalaryAdvanceStatus.APPROVED))) {
            throw new BadRequestException("ADVANCE_PENDING_EXISTS");
        }

        SalaryAdvance advance = new SalaryAdvance();
        advance.setEmployeeId(employeeId);
        advance.setRequestedAmount(requestedAmount);
        advance.setReason(reason);
        advance.setRequestedBy(requestedBy);
        advance.setStatus(SalaryAdvanceStatus.PENDING);
        SalaryAdvance saved = advanceRepo.save(advance);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "REQUESTED", null,
                Map.of("requestedAmount", requestedAmount.toPlainString()));
        return saved;
    }

    @Transactional
    public SalaryAdvance approve(UUID advanceId, BigDecimal approvedAmount,
                                  int repaymentYear, int repaymentMonth, String approvedBy) {
        SalaryAdvance advance = advanceRepo.findById(advanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary advance not found"));

        if (advance.getStatus() != SalaryAdvanceStatus.PENDING) {
            throw new BadRequestException("Advance is not pending");
        }

        advance.setApprovedAmount(approvedAmount);
        advance.setRepaymentYear(repaymentYear);
        advance.setRepaymentMonth(repaymentMonth);
        advance.setStatus(SalaryAdvanceStatus.APPROVED);
        advance.setApprovedBy(approvedBy);
        advance.setApprovedAt(OffsetDateTime.now());
        SalaryAdvance saved = advanceRepo.save(advance);

        audit.record(MODULE, ENTITY, advanceId.toString(), "APPROVED", null,
                Map.of("approvedAmount", approvedAmount.toPlainString(),
                        "repaymentPeriod", repaymentYear + "-" + String.format("%02d", repaymentMonth)));
        return saved;
    }

    @Transactional
    public SalaryAdvance reject(UUID advanceId, String reason) {
        SalaryAdvance advance = advanceRepo.findById(advanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary advance not found"));

        if (advance.getStatus() != SalaryAdvanceStatus.PENDING) {
            throw new BadRequestException("Advance is not pending");
        }

        advance.setStatus(SalaryAdvanceStatus.REJECTED);
        advance.setReason(reason);
        SalaryAdvance saved = advanceRepo.save(advance);

        audit.record(MODULE, ENTITY, advanceId.toString(), "REJECTED", null, Map.of("reason", reason));
        return saved;
    }

    @Transactional
    public SalaryAdvance cancel(UUID advanceId) {
        SalaryAdvance advance = advanceRepo.findById(advanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary advance not found"));

        if (advance.getStatus() == SalaryAdvanceStatus.DEDUCTED) {
            throw new BadRequestException("ADVANCE_ALREADY_DEDUCTED");
        }

        advance.setStatus(SalaryAdvanceStatus.CANCELLED);
        SalaryAdvance saved = advanceRepo.save(advance);

        audit.record(MODULE, ENTITY, advanceId.toString(), "CANCELLED", null, null);
        return saved;
    }

    /** Read a single advance (used for ownership checks). */
    @Transactional(readOnly = true)
    public SalaryAdvance get(UUID advanceId) {
        return advanceRepo.findById(advanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary advance not found"));
    }

    @Transactional
    public void markDeducted(UUID advanceId) {
        SalaryAdvance advance = advanceRepo.findById(advanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary advance not found"));
        advance.setStatus(SalaryAdvanceStatus.DEDUCTED);
        advance.setDisbursedAt(OffsetDateTime.now());
        advanceRepo.save(advance);
    }

    @Transactional(readOnly = true)
    public List<SalaryAdvance> outstandingForEmployee(UUID employeeId) {
        return advanceRepo.findByEmployeeIdAndStatusIn(employeeId, List.of(SalaryAdvanceStatus.APPROVED));
    }

    @Transactional(readOnly = true)
    public List<SalaryAdvance> findByEmployeeId(UUID employeeId) {
        return advanceRepo.findByEmployeeIdAndStatusIn(employeeId,
                List.of(SalaryAdvanceStatus.PENDING, SalaryAdvanceStatus.APPROVED,
                        SalaryAdvanceStatus.REJECTED, SalaryAdvanceStatus.DEDUCTED, SalaryAdvanceStatus.CANCELLED));
    }

    @Transactional(readOnly = true)
    public List<SalaryAdvance> findAll() {
        return advanceRepo.findAll();
    }
}
