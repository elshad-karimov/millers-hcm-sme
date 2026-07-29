package az.millers.hcm.compensation.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.api.dto.ChangeReasonRequest;
import az.millers.hcm.compensation.api.dto.ChangeReasonResponse;
import az.millers.hcm.compensation.domain.ChangeReason;
import az.millers.hcm.compensation.repo.ChangeReasonRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M360 — Change reason CRUD service.
 */
@Service
public class ChangeReasonService {

    private static final String MODULE = "compensation";
    private static final String ENTITY = "ChangeReason";

    private final ChangeReasonRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ChangeReasonService(ChangeReasonRepository repo,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<ChangeReason> listActive() {
        return repo.findByTenantIdAndIsActiveTrue(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public ChangeReason get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Change reason not found: " + id));
    }

    @Transactional
    public ChangeReason create(ChangeReasonRequest req) {
        if (repo.findByTenantIdAndCode(TenantContext.current(), req.code()).isPresent()) {
            throw new BadRequestException("Change reason code already exists: " + req.code());
        }

        ChangeReason reason = new ChangeReason();
        reason.setTenantId(TenantContext.current());
        applyRequest(reason, req);

        ChangeReason saved = repo.save(reason);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, ChangeReasonResponse.from(saved));
        return saved;
    }

    @Transactional
    public ChangeReason update(UUID id, ChangeReasonRequest req) {
        ChangeReason reason = get(id);
        ChangeReasonResponse before = ChangeReasonResponse.from(reason);

        // Check code uniqueness if changed
        if (!reason.getCode().equals(req.code())
                && repo.findByTenantIdAndCode(TenantContext.current(), req.code()).isPresent()) {
            throw new BadRequestException("Change reason code already exists: " + req.code());
        }

        applyRequest(reason, req);
        ChangeReason saved = repo.save(reason);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, ChangeReasonResponse.from(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        ChangeReason reason = get(id);
        ChangeReasonResponse before = ChangeReasonResponse.from(reason);

        // Soft delete
        reason.setIsActive(false);
        repo.save(reason);

        audit.record(MODULE, ENTITY, id.toString(),
                "DEACTIVATE", before, ChangeReasonResponse.from(reason));
    }

    private void applyRequest(ChangeReason reason, ChangeReasonRequest req) {
        reason.setCode(req.code());
        reason.setName(req.name());
        reason.setAffectsWorkflow(req.affectsWorkflow() != null ? req.affectsWorkflow() : true);
        reason.setCategory(req.category());
        if (req.isActive() != null) {
            reason.setIsActive(req.isActive());
        }
    }
}
