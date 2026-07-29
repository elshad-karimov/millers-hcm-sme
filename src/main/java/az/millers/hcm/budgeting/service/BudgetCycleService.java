package az.millers.hcm.budgeting.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.budgeting.domain.BudgetCycle;
import az.millers.hcm.budgeting.domain.BudgetCycleStatus;
import az.millers.hcm.budgeting.repo.BudgetCycleRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_20 M425 — Budget cycle management (PRD §20.2).
 * DRAFT → OPEN (accepts submissions) → LOCKED/CLOSED (immutable).
 */
@Service
public class BudgetCycleService {

    private static final String MODULE = "BUDGETING";

    private final BudgetCycleRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BudgetCycleService(BudgetCycleRepository repo,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<BudgetCycle> listAll() {
        return repo.findByTenantIdOrderByPeriodStartDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public List<BudgetCycle> listByStatus(BudgetCycleStatus status) {
        return repo.findByTenantIdAndStatusOrderByPeriodStartDesc(TenantContext.current(), status);
    }

    @Transactional(readOnly = true)
    public BudgetCycle get(UUID id) {
        BudgetCycle cycle = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget cycle not found: " + id));
        if (!TenantContext.current().equals(cycle.getTenantId())) {
            throw new ResourceNotFoundException("Budget cycle not found: " + id);
        }
        return cycle;
    }

    @Transactional
    public BudgetCycle create(BudgetCycle cycle) {
        if (cycle.getCode() == null || cycle.getCode().isBlank()) {
            throw new BadRequestException("Cycle code is required");
        }
        if (repo.findByTenantIdAndCode(TenantContext.current(), cycle.getCode()).isPresent()) {
            throw new BadRequestException("Cycle code already exists: " + cycle.getCode());
        }
        cycle.setTenantId(TenantContext.current());
        cycle.setCreatedBy(currentRequest.username());
        BudgetCycle saved = repo.save(cycle);
        audit.record(MODULE, "BudgetCycle", saved.getId().toString(),
                "CREATE", null, cycle.getCode());
        return saved;
    }

    @Transactional
    public BudgetCycle update(UUID id, BudgetCycle updated) {
        BudgetCycle existing = get(id);
        if (existing.getStatus() == BudgetCycleStatus.LOCKED
                || existing.getStatus() == BudgetCycleStatus.CLOSED) {
            throw new BadRequestException("Cannot update a LOCKED or CLOSED cycle");
        }
        existing.setName(updated.getName());
        existing.setCycleType(updated.getCycleType());
        existing.setPeriodStart(updated.getPeriodStart());
        existing.setPeriodEnd(updated.getPeriodEnd());
        existing.setSubmissionDeadline(updated.getSubmissionDeadline());
        BudgetCycle saved = repo.save(existing);
        audit.record(MODULE, "BudgetCycle", id.toString(), "UPDATE", null, null);
        return saved;
    }

    @Transactional
    public BudgetCycle updateStatus(UUID id, BudgetCycleStatus newStatus) {
        BudgetCycle cycle = get(id);
        BudgetCycleStatus before = cycle.getStatus();

        // Guards
        if (before == BudgetCycleStatus.CLOSED) {
            throw new BadRequestException("Cannot change status of a CLOSED cycle");
        }
        if (before == BudgetCycleStatus.LOCKED && newStatus != BudgetCycleStatus.CLOSED) {
            throw new BadRequestException("LOCKED cycle can only transition to CLOSED");
        }

        cycle.setStatus(newStatus);
        BudgetCycle saved = repo.save(cycle);
        audit.record(MODULE, "BudgetCycle", id.toString(),
                "STATUS_CHANGE", before.name(), newStatus.name());
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        BudgetCycle cycle = get(id);
        if (cycle.getStatus() != BudgetCycleStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT cycles can be deleted");
        }
        repo.deleteById(id);
        audit.record(MODULE, "BudgetCycle", id.toString(), "DELETE", null, null);
    }
}
