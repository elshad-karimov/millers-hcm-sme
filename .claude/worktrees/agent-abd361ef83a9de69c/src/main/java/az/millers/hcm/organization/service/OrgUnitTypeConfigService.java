package az.millers.hcm.organization.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.api.dto.OrgUnitTypeConfigDtos.OrgUnitTypeConfigRequest;
import az.millers.hcm.organization.api.dto.OrgUnitTypeConfigDtos.OrgUnitTypeConfigResponse;
import az.millers.hcm.organization.domain.OrgUnitTypeConfig;
import az.millers.hcm.organization.repo.OrgUnitTypeConfigRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M143 — CRUD for the org-unit type config registry (§5).
 *
 * <p>This service is also used by {@link OrgStructureService} to validate
 * the {@code unitType} string on every org-unit create/update.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Code is upper-case and immutable after creation.</li>
 *   <li>Deactivate instead of hard-delete; existing org units referencing the
 *       code are unaffected (the string is stored directly, no FK).</li>
 *   <li>{@code canHaveChildren} and {@code isRootLevel} are enforced by
 *       {@link OrgStructureService} at org-unit create/update time.</li>
 * </ul>
 */
@Service
public class OrgUnitTypeConfigService {

    private static final String MODULE = "ORGANIZATION";
    private static final String ENTITY = "OrgUnitTypeConfig";

    private final OrgUnitTypeConfigRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OrgUnitTypeConfigService(OrgUnitTypeConfigRepository repo,
                                     AuditService audit,
                                     CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<OrgUnitTypeConfig> list(boolean activeOnly) {
        return activeOnly
                ? repo.findByActiveTrueOrderBySortOrderAscCodeAsc()
                : repo.findAllByOrderBySortOrderAscCodeAsc();
    }

    @Transactional(readOnly = true)
    public OrgUnitTypeConfig get(String code) {
        return repo.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Org unit type not found: " + code));
    }

    /**
     * Validates that {@code typeCode} is an active, known type.
     * Called by {@link OrgStructureService} on every org-unit write.
     */
    @Transactional(readOnly = true)
    public OrgUnitTypeConfig validate(String typeCode) {
        OrgUnitTypeConfig cfg = repo.findById(typeCode)
                .orElseThrow(() -> new BadRequestException(
                        "Unknown org-unit type: " + typeCode + ". Register it in the type config first."));
        if (!cfg.isActive()) {
            throw new BadRequestException("Org-unit type '" + typeCode + "' is inactive.");
        }
        return cfg;
    }

    @Transactional
    public OrgUnitTypeConfig create(OrgUnitTypeConfigRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new BadRequestException("Org unit type code already exists: " + req.code());
        }
        OrgUnitTypeConfig c = new OrgUnitTypeConfig();
        apply(c, req);
        OrgUnitTypeConfig saved = repo.save(c);
        audit.record(MODULE, ENTITY, saved.getCode(), "CREATE",
                null, OrgUnitTypeConfigResponse.from(saved));
        return saved;
    }

    @Transactional
    public OrgUnitTypeConfig update(String code, OrgUnitTypeConfigRequest req) {
        OrgUnitTypeConfig c = get(code);
        OrgUnitTypeConfigResponse before = OrgUnitTypeConfigResponse.from(c);
        apply(c, req);
        OrgUnitTypeConfig saved = repo.save(c);
        audit.record(MODULE, ENTITY, code, "UPDATE",
                before, OrgUnitTypeConfigResponse.from(saved));
        return saved;
    }

    @Transactional
    public OrgUnitTypeConfig setActive(String code, boolean active) {
        OrgUnitTypeConfig c = get(code);
        if (c.isActive() == active) return c;
        OrgUnitTypeConfigResponse before = OrgUnitTypeConfigResponse.from(c);
        c.setActive(active);
        OrgUnitTypeConfig saved = repo.save(c);
        audit.record(MODULE, ENTITY, code, active ? "REACTIVATE" : "DEACTIVATE",
                before, OrgUnitTypeConfigResponse.from(saved));
        return saved;
    }

    private void apply(OrgUnitTypeConfig c, OrgUnitTypeConfigRequest req) {
        // Code is only written on create; ignored on update to keep it immutable.
        if (c.getCode() == null) c.setCode(req.code());
        c.setLabel(req.label());
        c.setColor(req.color());
        c.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        c.setCanHaveChildren(req.canHaveChildren() == null || req.canHaveChildren());
        c.setRootLevel(req.rootLevel() != null && req.rootLevel());
        c.setAllowedParentTypes(req.allowedParentTypes());
        c.setNotes(req.notes());
        if (req.active() != null) c.setActive(req.active());
    }
}
