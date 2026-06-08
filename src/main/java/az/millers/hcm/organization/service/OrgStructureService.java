package az.millers.hcm.organization.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.organization.domain.OrgUnitTypeConfig;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.api.dto.OrgTreeNode;
import az.millers.hcm.organization.api.dto.OrgUnitRequest;
import az.millers.hcm.organization.api.dto.OrgUnitResponse;
import az.millers.hcm.organization.api.dto.RollbackRequest;
import az.millers.hcm.organization.api.dto.StructureVersionRequest;
import az.millers.hcm.organization.api.dto.StructureVersionResponse;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.domain.StructureVersion;
import az.millers.hcm.organization.domain.VersionStatus;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.organization.repo.StructureVersionRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * Versioned organizational structure with rollback + a draft / approve /
 * activate state machine (PRD 8.2).
 */
@Service
public class OrgStructureService {

    private static final String MODULE = "ORGANIZATION";
    private static final String ENTITY_VERSION = "StructureVersion";
    private static final String ENTITY_UNIT = "OrgUnit";

    public static final String WORKFLOW_DEFINITION = "ORG_STRUCTURE_APPROVAL";

    private final StructureVersionRepository versions;
    private final OrgUnitRepository units;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final WorkflowService workflowService;
    private final OrgUnitHistoryService history;
    private final OrgUnitTypeConfigService typeConfigs;

    public OrgStructureService(StructureVersionRepository versions,
                               OrgUnitRepository units,
                               AuditService audit,
                               CurrentRequest currentRequest,
                               WorkflowService workflowService,
                               OrgUnitHistoryService history,
                               OrgUnitTypeConfigService typeConfigs) {
        this.versions = versions;
        this.units = units;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.workflowService = workflowService;
        this.history = history;
        this.typeConfigs = typeConfigs;
    }

    // ---------- Version queries ----------

    @Transactional(readOnly = true)
    public List<StructureVersion> listVersions() {
        return versions.findAllByOrderByVersionNumberDesc();
    }

    @Transactional(readOnly = true)
    public StructureVersion getVersion(UUID id) {
        return versions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Structure version not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<StructureVersion> getActiveVersion() {
        return versions.findFirstByStatus(VersionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<OrgUnit> unitsOf(UUID versionId) {
        return units.findByVersionIdOrderBySortOrderAscNameAsc(versionId);
    }

    @Transactional(readOnly = true)
    public OrgTreeNode buildTree(UUID versionId) {
        List<OrgUnit> all = unitsOf(versionId);
        Map<UUID, OrgTreeNode> byId = new HashMap<>();
        OrgTreeNode root = null;
        for (OrgUnit u : all) {
            byId.put(u.getId(), OrgTreeNode.leaf(OrgUnitResponse.from(u)));
        }
        for (OrgUnit u : all) {
            OrgTreeNode node = byId.get(u.getId());
            if (u.getParentId() == null) {
                root = node;
            } else {
                OrgTreeNode parent = byId.get(u.getParentId());
                if (parent != null) {
                    parent.children().add(node);
                }
            }
        }
        return root;
    }

    // ---------- Version lifecycle ----------

    @Transactional
    public StructureVersion createDraft(StructureVersionRequest req) {
        StructureVersion v = newVersion(req.effectiveDate(), req.changeReason(),
                null, VersionStatus.DRAFT);
        StructureVersion saved = versions.save(v);
        audit.record(MODULE, ENTITY_VERSION, saved.getId().toString(), "CREATE_DRAFT",
                null, StructureVersionResponse.from(saved));
        return saved;
    }

    @Transactional
    public StructureVersion submitForApproval(UUID versionId) {
        StructureVersion v = getVersion(versionId);
        require(v.getStatus() == VersionStatus.DRAFT,
                "Only a DRAFT version can be submitted for approval");
        require(units.countByVersionId(versionId) > 0,
                "Cannot submit an empty version");
        StructureVersion saved = transition(v, VersionStatus.PENDING_APPROVAL, "SUBMIT_FOR_APPROVAL", null);
        workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY_VERSION,
                saved.getId().toString(),
                "Org structure v" + saved.getVersionNumber() + " — effective " + saved.getEffectiveDate(),
                Map.of(
                        "versionNumber", saved.getVersionNumber(),
                        "effectiveDate", saved.getEffectiveDate().toString(),
                        "changeReason", saved.getChangeReason() == null ? "" : saved.getChangeReason())));
        return saved;
    }

    /** Called by the workflow listener once approval completes. */
    @Transactional
    public StructureVersion markApproved(UUID versionId, String reason) {
        StructureVersion v = getVersion(versionId);
        if (v.getStatus() == VersionStatus.APPROVED) return v;
        require(v.getStatus() == VersionStatus.PENDING_APPROVAL,
                "Cannot mark approved from status " + v.getStatus());
        v.setApprovedBy(currentRequest.username());
        return transition(v, VersionStatus.APPROVED, "APPROVE", reason);
    }

    @Transactional
    public StructureVersion markRejected(UUID versionId, String reason) {
        StructureVersion v = getVersion(versionId);
        if (v.getStatus() == VersionStatus.REJECTED) return v;
        require(v.getStatus() == VersionStatus.PENDING_APPROVAL,
                "Cannot mark rejected from status " + v.getStatus());
        return transition(v, VersionStatus.REJECTED, "REJECT", reason);
    }

    @Transactional
    public StructureVersion returnToDraft(UUID versionId, String reason) {
        StructureVersion v = getVersion(versionId);
        if (v.getStatus() == VersionStatus.DRAFT) return v;
        require(v.getStatus() == VersionStatus.PENDING_APPROVAL,
                "Cannot return to draft from status " + v.getStatus());
        return transition(v, VersionStatus.DRAFT, "RETURN_TO_DRAFT", reason);
    }

    @Transactional
    public StructureVersion approve(UUID versionId, String reason) {
        StructureVersion v = getVersion(versionId);
        require(v.getStatus() == VersionStatus.PENDING_APPROVAL,
                "Only a PENDING_APPROVAL version can be approved");
        v.setApprovedBy(currentRequest.username());
        return transition(v, VersionStatus.APPROVED, "APPROVE", reason);
    }

    @Transactional
    public StructureVersion reject(UUID versionId, String reason) {
        StructureVersion v = getVersion(versionId);
        require(v.getStatus() == VersionStatus.PENDING_APPROVAL,
                "Only a PENDING_APPROVAL version can be rejected");
        return transition(v, VersionStatus.REJECTED, "REJECT", reason);
    }

    @Transactional
    public StructureVersion activate(UUID versionId) {
        StructureVersion v = getVersion(versionId);
        require(v.getStatus() == VersionStatus.APPROVED,
                "Only an APPROVED version can be activated");

        versions.findFirstByStatus(VersionStatus.ACTIVE).ifPresent(current -> {
            VersionStatus old = current.getStatus();
            current.setStatus(VersionStatus.ARCHIVED);
            current.setArchivedAt(OffsetDateTime.now());
            // Flush immediately so the partial unique index on status='ACTIVE'
            // sees the archive before we promote `v` to ACTIVE in the same tx.
            versions.saveAndFlush(current);
            audit.record(MODULE, ENTITY_VERSION, current.getId().toString(),
                    "ARCHIVE",
                    Map.of("status", old.name()),
                    Map.of("status", VersionStatus.ARCHIVED.name(),
                            "supersededBy", v.getId().toString()));
        });

        v.setActivatedAt(OffsetDateTime.now());
        return transition(v, VersionStatus.ACTIVE, "ACTIVATE", null);
    }

    /**
     * Rollback to a past version. Creates a new PENDING_APPROVAL version that
     * mirrors the source's unit tree (PRD 8.2.7 acceptance criterion).
     */
    @Transactional
    public StructureVersion rollback(RollbackRequest req) {
        StructureVersion source = getVersion(req.sourceVersionId());

        StructureVersion clone = newVersion(req.effectiveDate(),
                "Rollback to v" + source.getVersionNumber()
                        + (StringUtils.hasText(req.reason()) ? ": " + req.reason() : ""),
                source.getId(),
                VersionStatus.PENDING_APPROVAL);
        StructureVersion savedClone = versions.save(clone);

        cloneUnits(source.getId(), savedClone.getId());

        audit.record(MODULE, ENTITY_VERSION, savedClone.getId().toString(),
                "ROLLBACK",
                Map.of("sourceVersionId", source.getId().toString(),
                        "sourceVersionNumber", source.getVersionNumber()),
                StructureVersionResponse.from(savedClone));
        return savedClone;
    }

    // ---------- Unit edits (DRAFT only) ----------

    @Transactional
    public OrgUnit addUnit(UUID versionId, OrgUnitRequest req) {
        StructureVersion v = getVersion(versionId);
        requireDraft(v);
        if (units.existsByVersionIdAndCode(versionId, req.code())) {
            throw new BadRequestException("Unit code already used in this version: " + req.code());
        }
        OrgUnitTypeConfig typeCfg = typeConfigs.validate(req.unitType());
        if (req.parentId() != null) {
            OrgUnit parent = findUnit(req.parentId());
            require(parent.getVersionId().equals(versionId),
                    "Parent must belong to the same version");
            // M143 — enforce canHaveChildren on the parent type.
            if (!typeConfigs.validate(parent.getUnitType()).isCanHaveChildren()) {
                throw new BadRequestException(
                        "Parent unit type '" + parent.getUnitType() + "' does not allow child units");
            }
        } else {
            // root unit — type must allow it
            if (!typeCfg.isRootLevel()) {
                throw new BadRequestException(
                        "Unit type '" + req.unitType() + "' is not allowed at the root level (no parent)");
            }
        }
        OrgUnit u = new OrgUnit();
        u.setVersionId(versionId);
        applyRequest(u, req);
        OrgUnit saved = units.save(u);
        history.record(saved.getId(), versionId,
                az.millers.hcm.organization.domain.OrgUnitHistory.ChangeKind.CREATE,
                null, OrgUnitResponse.from(saved), null);
        audit.record(MODULE, ENTITY_UNIT, saved.getId().toString(),
                "ADD_UNIT", null, OrgUnitResponse.from(saved));
        return saved;
    }

    @Transactional
    public OrgUnit updateUnit(UUID unitId, OrgUnitRequest req) {
        OrgUnit u = findUnit(unitId);
        StructureVersion v = getVersion(u.getVersionId());
        requireDraft(v);
        if (!u.getCode().equals(req.code())
                && units.existsByVersionIdAndCode(u.getVersionId(), req.code())) {
            throw new BadRequestException("Unit code already used in this version: " + req.code());
        }
        // M143 — validate the requested type.
        typeConfigs.validate(req.unitType());
        if (req.parentId() != null) {
            if (req.parentId().equals(unitId)) {
                throw new BadRequestException("A unit cannot be its own parent");
            }
            OrgUnit parent = findUnit(req.parentId());
            require(parent.getVersionId().equals(u.getVersionId()),
                    "Parent must belong to the same version");
        }
        OrgUnitResponse before = OrgUnitResponse.from(u);
        applyRequest(u, req);
        OrgUnit saved = units.save(u);
        OrgUnitResponse after = OrgUnitResponse.from(saved);
        // Classify the change so the per-unit timeline shows the most useful
        // kind label instead of generic UPDATE.
        var kind = az.millers.hcm.organization.domain.OrgUnitHistory.ChangeKind.UPDATE;
        if (before.parentId() != null
                ? !before.parentId().equals(after.parentId())
                : after.parentId() != null) {
            kind = az.millers.hcm.organization.domain.OrgUnitHistory.ChangeKind.MOVE;
        } else if (!before.name().equals(after.name())) {
            kind = az.millers.hcm.organization.domain.OrgUnitHistory.ChangeKind.RENAME;
        } else if (before.headEmployeeId() != null
                ? !before.headEmployeeId().equals(after.headEmployeeId())
                : after.headEmployeeId() != null) {
            kind = az.millers.hcm.organization.domain.OrgUnitHistory.ChangeKind.HEAD_CHANGE;
        }
        history.record(saved.getId(), saved.getVersionId(), kind, before, after, null);
        audit.record(MODULE, ENTITY_UNIT, saved.getId().toString(),
                "UPDATE_UNIT", before, after);
        return saved;
    }

    @Transactional
    public void removeUnit(UUID unitId) {
        OrgUnit u = findUnit(unitId);
        StructureVersion v = getVersion(u.getVersionId());
        requireDraft(v);
        if (units.existsByParentId(unitId)) {
            throw new BadRequestException("Remove or re-parent the children first");
        }
        OrgUnitResponse before = OrgUnitResponse.from(u);
        UUID vId = u.getVersionId();
        units.delete(u);
        history.record(unitId, vId,
                az.millers.hcm.organization.domain.OrgUnitHistory.ChangeKind.REMOVE,
                before, null, null);
        audit.record(MODULE, ENTITY_UNIT, unitId.toString(),
                "REMOVE_UNIT", before, null);
    }

    // ---------- Internals ----------

    private StructureVersion newVersion(LocalDate effectiveDate, String reason,
                                        UUID previousVersionId, VersionStatus status) {
        StructureVersion v = new StructureVersion();
        v.setVersionNumber((int) versions.nextVersionNumber());
        v.setEffectiveDate(effectiveDate);
        v.setChangeReason(reason);
        v.setPreviousVersionId(previousVersionId);
        v.setStatus(status);
        v.setCreatedBy(currentRequest.username());
        return v;
    }

    private StructureVersion transition(StructureVersion v, VersionStatus next,
                                        String action, String reason) {
        VersionStatus old = v.getStatus();
        v.setStatus(next);
        if (StringUtils.hasText(reason)) {
            v.setChangeReason(reason);
        }
        StructureVersion saved = versions.save(v);
        audit.record(MODULE, ENTITY_VERSION, saved.getId().toString(),
                action,
                Map.of("status", old.name()),
                Map.of("status", next.name(),
                        "reason", reason == null ? "" : reason));
        return saved;
    }

    private void cloneUnits(UUID sourceVersionId, UUID destVersionId) {
        List<OrgUnit> source = unitsOf(sourceVersionId);
        Map<UUID, UUID> idMap = new HashMap<>();
        // First pass: create new units, capture id mapping.
        List<OrgUnit> clones = new ArrayList<>(source.size());
        for (OrgUnit src : source) {
            OrgUnit clone = new OrgUnit();
            clone.setVersionId(destVersionId);
            clone.setCode(src.getCode());
            clone.setName(src.getName());
            clone.setUnitType(src.getUnitType());
            clone.setHeadEmployeeId(src.getHeadEmployeeId());
            clone.setSortOrder(src.getSortOrder());
            clone.setLocationId(src.getLocationId());
            clone.setHrbpId(src.getHrbpId());
            clone.setCostCentreCode(src.getCostCentreCode());
            clone.setLocation(src.getLocation());
            clone.setContactEmail(src.getContactEmail());
            clone.setGlAccount(src.getGlAccount());
            clone.setHeadcountBudget(src.getHeadcountBudget());
            clone.setActive(src.isActive());
            clone.setLifecycleState(src.getLifecycleState());
            clone.setPlannedOpenDate(src.getPlannedOpenDate());
            clone.setClosureAnnouncedDate(src.getClosureAnnouncedDate());
            clone.setClosureReason(src.getClosureReason());
            clone.setClosedDate(src.getClosedDate());
            clone.setClosedBy(src.getClosedBy());
            clone.setLegalEntityId(src.getLegalEntityId());
            // M148 — Branch/Store enrichment fields.
            clone.setGpsLat(src.getGpsLat());
            clone.setGpsLng(src.getGpsLng());
            clone.setOperatingHours(src.getOperatingHours());
            clone.setAttendanceDeviceId(src.getAttendanceDeviceId());
            clone.setPosSystemRef(src.getPosSystemRef());
            // Parent set in second pass once we know mappings.
            OrgUnit saved = units.save(clone);
            idMap.put(src.getId(), saved.getId());
            clones.add(saved);
        }
        // Second pass: rewire parents through the id map.
        for (int i = 0; i < source.size(); i++) {
            OrgUnit src = source.get(i);
            if (src.getParentId() == null) continue;
            OrgUnit clone = clones.get(i);
            clone.setParentId(idMap.get(src.getParentId()));
            units.save(clone);
        }
    }

    private OrgUnit findUnit(UUID id) {
        return units.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Org unit not found: " + id));
    }

    private void applyRequest(OrgUnit u, OrgUnitRequest req) {
        u.setCode(req.code());
        u.setName(req.name());
        u.setUnitType(req.unitType());
        u.setParentId(req.parentId());
        u.setHeadEmployeeId(req.headEmployeeId());
        u.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        // M141 — structured location FK.
        u.setLocationId(req.locationId());
        // M142 — primary HRBP.
        u.setHrbpId(req.hrbpId());
        // M81 — extended attributes. Null = clear / not set.
        u.setCostCentreCode(req.costCentreCode());
        u.setLocation(req.location());
        u.setContactEmail(req.contactEmail());
        u.setGlAccount(req.glAccount());
        u.setHeadcountBudget(req.headcountBudget());
        // Boolean wrapper so null on update keeps existing value.
        if (req.active() != null) u.setActive(req.active());
        // M144 — lifecycle state; null on create defaults to ACTIVE.
        if (req.lifecycleState() != null) {
            u.setLifecycleState(az.millers.hcm.organization.domain.OrgUnitLifecycleState
                    .valueOf(req.lifecycleState()));
        } else if (u.getLifecycleState() == null) {
            u.setLifecycleState(az.millers.hcm.organization.domain.OrgUnitLifecycleState.ACTIVE);
        }
        // M148 / §28 — Branch/Store enrichment.
        u.setGpsLat(req.gpsLat());
        u.setGpsLng(req.gpsLng());
        u.setOperatingHours(req.operatingHours());
        u.setAttendanceDeviceId(req.attendanceDeviceId());
        u.setPosSystemRef(req.posSystemRef());
    }

    private void requireDraft(StructureVersion v) {
        require(v.getStatus() == VersionStatus.DRAFT,
                "Units can only be edited while the version is DRAFT (current: " + v.getStatus() + ")");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BadRequestException(message);
        }
    }
}
