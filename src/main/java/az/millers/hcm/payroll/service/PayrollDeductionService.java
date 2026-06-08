package az.millers.hcm.payroll.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.api.dto.PayrollDeductionRequest;
import az.millers.hcm.payroll.domain.PayrollDeduction;
import az.millers.hcm.payroll.repo.PayrollDeductionRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class PayrollDeductionService {

    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "PayrollDeduction";

    private final PayrollDeductionRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PayrollDeductionService(PayrollDeductionRepository repo,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public Page<PayrollDeduction> list(UUID employeeId, String status, Pageable pageable) {
        if (status != null) {
            return repo.findByEmployeeIdAndStatusOrderByCreatedAtDesc(employeeId, status, pageable);
        }
        return repo.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
    }

    @Transactional(readOnly = true)
    public PayrollDeduction get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deduction not found: " + id));
    }

    @Transactional
    public PayrollDeduction create(PayrollDeductionRequest req) {
        validate(req);
        PayrollDeduction d = new PayrollDeduction();
        d.setEmployeeId(req.employeeId());
        d.setDeductionType(req.deductionType());
        d.setDescription(req.description());
        d.setAmountPerPeriod(req.amountPerPeriod());
        d.setTotalAmount(req.totalAmount());
        d.setStartPeriodYear(req.startPeriodYear());
        d.setStartPeriodMonth(req.startPeriodMonth());
        d.setEndPeriodYear(req.endPeriodYear());
        d.setEndPeriodMonth(req.endPeriodMonth());
        d.setNote(req.note());
        d.setCreatedBy(currentRequest.username());
        d.setStatus("ACTIVE");
        PayrollDeduction saved = repo.save(d);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public PayrollDeduction cancel(UUID id) {
        PayrollDeduction d = get(id);
        if (!"ACTIVE".equals(d.getStatus())) {
            throw new BadRequestException("Only ACTIVE deductions can be cancelled");
        }
        d.setStatus("CANCELLED");
        PayrollDeduction saved = repo.save(d);
        audit.record(MODULE, ENTITY, id.toString(), "CANCEL", null, saved);
        return saved;
    }

    private void validate(PayrollDeductionRequest req) {
        if ("ADVANCE_RECOVERY".equals(req.deductionType()) && req.totalAmount() == null) {
            throw new BadRequestException("totalAmount is required for ADVANCE_RECOVERY deductions");
        }
        if (req.endPeriodYear() != null || req.endPeriodMonth() != null) {
            if (req.endPeriodYear() == null || req.endPeriodMonth() == null) {
                throw new BadRequestException("endPeriodYear and endPeriodMonth must both be set or both null");
            }
            int start = req.startPeriodYear() * 100 + req.startPeriodMonth();
            int end   = req.endPeriodYear()   * 100 + req.endPeriodMonth();
            if (end < start) {
                throw new BadRequestException("end period must not be before start period");
            }
        }
    }
}
