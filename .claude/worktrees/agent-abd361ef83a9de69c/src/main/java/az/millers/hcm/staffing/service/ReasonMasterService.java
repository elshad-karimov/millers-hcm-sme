package az.millers.hcm.staffing.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.staffing.api.dto.ReasonMasterDtos.ReasonRequest;
import az.millers.hcm.staffing.api.dto.ReasonMasterDtos.ReasonResponse;
import az.millers.hcm.staffing.domain.ReasonCategory;
import az.millers.hcm.staffing.domain.ReasonMaster;
import az.millers.hcm.staffing.repo.ReasonMasterRepository;

/** M259 — Reason master service (PRD §22). */
@Service
public class ReasonMasterService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "ReasonMaster";

    private final ReasonMasterRepository repo;
    private final AuditService audit;

    public ReasonMasterService(ReasonMasterRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ReasonResponse> list(ReasonCategory category, boolean includeInactive) {
        List<ReasonMaster> rows = includeInactive
                ? repo.findByCategoryOrderBySortOrderAscLabelAsc(category)
                : repo.findByCategoryAndActiveTrueOrderBySortOrderAscLabelAsc(category);
        return rows.stream().map(ReasonResponse::from).toList();
    }

    @Transactional
    public ReasonResponse create(ReasonRequest req) {
        ReasonMaster r = new ReasonMaster();
        apply(r, req);
        ReasonMaster saved = repo.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, ReasonResponse.from(saved));
        return ReasonResponse.from(saved);
    }

    @Transactional
    public ReasonResponse update(UUID id, ReasonRequest req) {
        ReasonMaster r = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reason not found: " + id));
        ReasonResponse before = ReasonResponse.from(r);
        apply(r, req);
        ReasonMaster saved = repo.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPDATE", before, ReasonResponse.from(saved));
        return ReasonResponse.from(saved);
    }

    /** Soft delete — flip active off so historical breadcrumbs still resolve. */
    @Transactional
    public void deactivate(UUID id) {
        ReasonMaster r = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reason not found: " + id));
        ReasonResponse before = ReasonResponse.from(r);
        r.setActive(false);
        ReasonMaster saved = repo.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "DEACTIVATE", before, ReasonResponse.from(saved));
    }

    private void apply(ReasonMaster r, ReasonRequest req) {
        r.setCategory(req.category());
        r.setCode(req.code());
        r.setLabel(req.label());
        r.setDescription(req.description());
        if (req.active() != null) r.setActive(req.active());
        if (req.sortOrder() != null) r.setSortOrder(req.sortOrder());
    }
}
