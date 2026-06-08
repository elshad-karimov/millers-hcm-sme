package az.millers.hcm.organization.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.api.dto.BulkReorgManifest;
import az.millers.hcm.organization.api.dto.BulkReorgManifest.BulkReorgOperation;
import az.millers.hcm.organization.api.dto.BulkReorgManifest.BulkReorgOperation.OperationKind;
import az.millers.hcm.organization.api.dto.BulkReorgResult;
import az.millers.hcm.organization.api.dto.BulkReorgResult.RowResult;
import az.millers.hcm.organization.api.dto.OrgUnitRequest;
import az.millers.hcm.organization.api.dto.OrgUnitResponse;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.domain.OrgUnitType;
import az.millers.hcm.organization.repo.OrgUnitRepository;

/**
 * Apply many org changes atomically (M84).
 *
 * <p>Two-phase: pre-flight validates the whole manifest, then apply walks
 * the same operations through {@link OrgStructureService}. The service is
 * a single transaction — a {@code RuntimeException} on any operation rolls
 * back every prior step.
 *
 * <p>Operations are keyed by {@code code} (not UUID) so the manifest can
 * reference units it just added in the same batch. A working map of
 * code → OrgUnit is maintained across the batch; ADDs insert into it, and
 * subsequent UPDATE/MOVE/REMOVE rows resolve through it.
 */
@Service
public class BulkReorgService {

    private static final String MODULE = "ORGANIZATION";
    private static final String ENTITY_UNIT = "OrgUnit";

    private final OrgStructureService orgService;
    private final OrgUnitRepository units;
    private final AuditService audit;

    public BulkReorgService(OrgStructureService orgService,
                             OrgUnitRepository units,
                             AuditService audit) {
        this.orgService = orgService;
        this.units = units;
        this.audit = audit;
    }

    @Transactional
    public BulkReorgResult apply(UUID versionId, BulkReorgManifest manifest) {
        // Snapshot: code → OrgUnit, populated for the target version. ADDs
        // insert into it; UPDATE/MOVE/REMOVE resolve through it; later ops
        // see the cumulative effect of earlier ones.
        Map<String, OrgUnit> byCode = new HashMap<>();
        for (OrgUnit u : units.findByVersionIdOrderBySortOrderAscNameAsc(versionId)) {
            byCode.put(u.getCode(), u);
        }

        List<BulkReorgOperation> ops = manifest.operations();
        List<RowResult> rows = new ArrayList<>(ops.size());

        // ── Pre-flight: validate everything. We simulate ADDs into the
        // byCode map so subsequent references to those codes pass; the real
        // mutation is the second pass. Validation is cheap and reduces the
        // blast radius of bad data.
        Map<String, Boolean> simAdded = new HashMap<>();
        for (int i = 0; i < ops.size(); i++) {
            BulkReorgOperation op = ops.get(i);
            String code = nonBlank(op.code(), "code", i);
            switch (op.kind()) {
                case ADD -> {
                    if (byCode.containsKey(code) || simAdded.containsKey(code)) {
                        throw new BadRequestException(
                                "Row " + i + ": ADD — code already in version: " + code);
                    }
                    nonBlank(op.name(), "name", i);
                    if (op.unitType() == null) {
                        throw new BadRequestException("Row " + i + ": ADD — unitType is required");
                    }
                    if (op.parentCode() != null
                            && !byCode.containsKey(op.parentCode())
                            && !simAdded.containsKey(op.parentCode())) {
                        throw new BadRequestException(
                                "Row " + i + ": ADD — parentCode unknown: " + op.parentCode());
                    }
                    simAdded.put(code, true);
                }
                case UPDATE -> {
                    if (!byCode.containsKey(code) && !simAdded.containsKey(code)) {
                        throw new BadRequestException(
                                "Row " + i + ": UPDATE — code does not exist: " + code);
                    }
                }
                case MOVE -> {
                    if (!byCode.containsKey(code) && !simAdded.containsKey(code)) {
                        throw new BadRequestException(
                                "Row " + i + ": MOVE — code does not exist: " + code);
                    }
                    if (op.newParentCode() == null) {
                        throw new BadRequestException(
                                "Row " + i + ": MOVE — newParentCode is required");
                    }
                    if (!byCode.containsKey(op.newParentCode())
                            && !simAdded.containsKey(op.newParentCode())) {
                        throw new BadRequestException(
                                "Row " + i + ": MOVE — newParentCode unknown: "
                                        + op.newParentCode());
                    }
                    if (code.equals(op.newParentCode())) {
                        throw new BadRequestException(
                                "Row " + i + ": MOVE — a unit cannot be its own parent");
                    }
                }
                case REMOVE -> {
                    if (!byCode.containsKey(code) && !simAdded.containsKey(code)) {
                        throw new BadRequestException(
                                "Row " + i + ": REMOVE — code does not exist: " + code);
                    }
                }
            }
        }

        if (manifest.dryRun()) {
            for (int i = 0; i < ops.size(); i++) {
                rows.add(new RowResult(i, ops.get(i).kind(), ops.get(i).code(),
                        false, "dry-run — would apply"));
            }
            audit.record(MODULE, "BulkReorg", versionId.toString(),
                    "DRYRUN", null, summary(ops));
            return new BulkReorgResult(true, ops.size(), 0, rows);
        }

        // ── Apply phase. The pre-flight already ensured every reference
        // resolves and there are no name collisions; an unexpected failure
        // here (DB constraint, scope check) propagates and rolls back the
        // whole transaction.
        for (int i = 0; i < ops.size(); i++) {
            BulkReorgOperation op = ops.get(i);
            try {
                switch (op.kind()) {
                    case ADD -> {
                        OrgUnit parent = op.parentCode() == null ? null : byCode.get(op.parentCode());
                        OrgUnitRequest req = new OrgUnitRequest(
                                op.code(), op.name(), op.unitType(),
                                parent == null ? null : parent.getId(),
                                op.headEmployeeId(),
                                op.sortOrder(),
                                null,
                                op.costCentreCode(), op.location(),
                                op.contactEmail(), op.glAccount(),
                                op.headcountBudget(), op.active());
                        OrgUnit saved = orgService.addUnit(versionId, req);
                        byCode.put(saved.getCode(), saved);
                        rows.add(new RowResult(i, OperationKind.ADD, op.code(), true, "added"));
                    }
                    case UPDATE -> {
                        OrgUnit existing = byCode.get(op.code());
                        OrgUnitRequest req = new OrgUnitRequest(
                                existing.getCode(),
                                pickStr(op.name(), existing.getName()),
                                pickType(op.unitType(), existing.getUnitType()),
                                resolveParent(op.parentCode(), existing.getParentId(), byCode),
                                op.headEmployeeId() != null ? op.headEmployeeId() : existing.getHeadEmployeeId(),
                                op.sortOrder() != null ? op.sortOrder() : existing.getSortOrder(),
                                existing.getLocationId(),
                                pickStr(op.costCentreCode(), existing.getCostCentreCode()),
                                pickStr(op.location(), existing.getLocation()),
                                pickStr(op.contactEmail(), existing.getContactEmail()),
                                pickStr(op.glAccount(), existing.getGlAccount()),
                                op.headcountBudget() != null ? op.headcountBudget() : existing.getHeadcountBudget(),
                                op.active());
                        OrgUnit saved = orgService.updateUnit(existing.getId(), req);
                        byCode.put(saved.getCode(), saved);
                        rows.add(new RowResult(i, OperationKind.UPDATE, op.code(), true, "updated"));
                    }
                    case MOVE -> {
                        OrgUnit existing = byCode.get(op.code());
                        OrgUnit newParent = byCode.get(op.newParentCode());
                        OrgUnitRequest req = new OrgUnitRequest(
                                existing.getCode(),
                                existing.getName(),
                                existing.getUnitType(),
                                newParent.getId(),
                                existing.getHeadEmployeeId(),
                                existing.getSortOrder(),
                                existing.getLocationId(),
                                existing.getCostCentreCode(),
                                existing.getLocation(),
                                existing.getContactEmail(),
                                existing.getGlAccount(),
                                existing.getHeadcountBudget(),
                                existing.isActive());
                        OrgUnit saved = orgService.updateUnit(existing.getId(), req);
                        byCode.put(saved.getCode(), saved);
                        rows.add(new RowResult(i, OperationKind.MOVE, op.code(),
                                true, "moved under " + op.newParentCode()));
                    }
                    case REMOVE -> {
                        OrgUnit existing = byCode.get(op.code());
                        orgService.removeUnit(existing.getId());
                        byCode.remove(op.code());
                        rows.add(new RowResult(i, OperationKind.REMOVE, op.code(),
                                true, "removed"));
                    }
                }
            } catch (ResourceNotFoundException e) {
                // shouldn't happen given the pre-flight, but if it does we
                // want a clear row-level message rather than a 404.
                throw new BadRequestException("Row " + i + ": " + e.getMessage());
            }
        }

        audit.record(MODULE, "BulkReorg", versionId.toString(),
                "APPLY", null, summary(ops));
        return new BulkReorgResult(false, ops.size(), rows.size(), rows);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String nonBlank(String value, String field, int index) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Row " + index + ": " + field + " is required");
        }
        return value;
    }

    private static String pickStr(String incoming, String existing) {
        return incoming != null ? incoming : existing;
    }

    private static OrgUnitType pickType(OrgUnitType incoming, OrgUnitType existing) {
        return incoming != null ? incoming : existing;
    }

    /**
     * For UPDATE rows where parentCode is not specified, preserve the
     * existing parent id. When parentCode is explicitly given, resolve it
     * through the working map.
     */
    private static UUID resolveParent(String parentCode, UUID existingParentId,
                                       Map<String, OrgUnit> byCode) {
        if (parentCode == null) return existingParentId;
        OrgUnit p = byCode.get(parentCode);
        return p == null ? null : p.getId();
    }

    private static Map<String, Object> summary(List<BulkReorgOperation> ops) {
        Map<String, Integer> counts = new HashMap<>();
        for (BulkReorgOperation op : ops) {
            counts.merge(op.kind().name(), 1, Integer::sum);
        }
        return Map.of("operations", ops.size(), "byKind", counts);
    }

    @SuppressWarnings("unused")
    private static String responseCode(OrgUnitResponse r) {
        return r == null ? null : r.code();
    }
}
