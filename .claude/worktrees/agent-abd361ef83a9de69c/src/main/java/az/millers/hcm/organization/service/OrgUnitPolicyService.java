package az.millers.hcm.organization.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.domain.OrgUnitHistory.ChangeKind;
import az.millers.hcm.organization.domain.OrgUnitPolicy;
import az.millers.hcm.organization.repo.OrgUnitPolicyRepository;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Per-(org_unit, version) policy resolver (M81).
 *
 * <p>{@link #resolveLeaveGroup(UUID, UUID, UUID)} +
 * {@link #resolvePayrollGroup(UUID, UUID, UUID)} walk up the org tree from
 * the supplied unit until they find a non-null override. The caller passes
 * an override (from the employee row itself) which short-circuits the walk.
 * If the walk completes without a hit, the resolver returns {@code null} and
 * the caller falls back to the seeded system default group.
 *
 * <p>This is the M66 / M75 fallback mechanic generalised one level up — the
 * default group catches the floor, the unit policy override catches per-team
 * exceptions without needing per-employee per-row writes.
 */
@Service
public class OrgUnitPolicyService {

    private static final String MODULE = "ORG_STRUCTURE";
    private static final String ENTITY = "OrgUnitPolicy";

    private final OrgUnitPolicyRepository policies;
    private final OrgUnitRepository units;
    private final OrgUnitHistoryService history;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OrgUnitPolicyService(OrgUnitPolicyRepository policies,
                                 OrgUnitRepository units,
                                 OrgUnitHistoryService history,
                                 AuditService audit,
                                 CurrentRequest currentRequest) {
        this.policies = policies;
        this.units = units;
        this.history = history;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrgUnitPolicy get(UUID orgUnitId, UUID versionId) {
        return policies.findByOrgUnitIdAndVersionId(orgUnitId, versionId).orElse(null);
    }

    /**
     * Walks up the org tree returning the first non-null leaveGroupId
     * override. Returns the {@code employeeOverride} immediately if non-null
     * — the caller's own row trumps any unit policy.
     */
    @Transactional(readOnly = true)
    public UUID resolveLeaveGroup(UUID employeeOverride, UUID orgUnitId, UUID versionId) {
        if (employeeOverride != null) return employeeOverride;
        return walkUp(orgUnitId, versionId, OrgUnitPolicy::getLeaveGroupId);
    }

    @Transactional(readOnly = true)
    public UUID resolvePayrollGroup(UUID employeeOverride, UUID orgUnitId, UUID versionId) {
        if (employeeOverride != null) return employeeOverride;
        return walkUp(orgUnitId, versionId, OrgUnitPolicy::getPayrollGroupId);
    }

    // ── Mutation ─────────────────────────────────────────────────────────────

    /**
     * Upsert the policy row for a (unit, version). One row per pair — the
     * UNIQUE index on the table enforces this; we delegate to JPA via
     * findByOrgUnitIdAndVersionId.
     */
    @Transactional
    public OrgUnitPolicy upsert(UUID orgUnitId, UUID versionId,
                                 UUID leaveGroupId, UUID payrollGroupId, String notes) {
        OrgUnit unit = units.findById(orgUnitId)
                .orElseThrow(() -> new BadRequestException("OrgUnit not found: " + orgUnitId));
        if (!unit.getVersionId().equals(versionId)) {
            throw new BadRequestException(
                    "OrgUnit " + orgUnitId + " does not belong to version " + versionId);
        }
        OrgUnitPolicy existing = policies.findByOrgUnitIdAndVersionId(orgUnitId, versionId).orElse(null);
        boolean isCreate = existing == null;
        OrgUnitPolicy p = existing == null ? new OrgUnitPolicy() : existing;
        Object before = existing == null ? null : snapshot(existing);
        if (existing == null) {
            p.setOrgUnitId(orgUnitId);
            p.setVersionId(versionId);
            p.setCreatedBy(currentRequest.username());
        }
        p.setLeaveGroupId(leaveGroupId);
        p.setPayrollGroupId(payrollGroupId);
        p.setNotes(notes);
        p.setUpdatedBy(currentRequest.username());
        OrgUnitPolicy saved = policies.save(p);

        history.record(orgUnitId, versionId, ChangeKind.POLICY_CHANGE,
                before, snapshot(saved), null);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                isCreate ? "CREATE" : "UPDATE", before, snapshot(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID orgUnitId, UUID versionId) {
        OrgUnitPolicy existing = policies.findByOrgUnitIdAndVersionId(orgUnitId, versionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Policy not found for unit " + orgUnitId + " in version " + versionId));
        Object before = snapshot(existing);
        policies.delete(existing);
        history.record(orgUnitId, versionId, ChangeKind.POLICY_CHANGE,
                before, null, "Policy removed");
        audit.record(MODULE, ENTITY, existing.getId().toString(),
                "DELETE", before, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID walkUp(UUID orgUnitId, UUID versionId,
                        java.util.function.Function<OrgUnitPolicy, UUID> pick) {
        UUID currentId = orgUnitId;
        int depth = 0;
        while (currentId != null && depth < 32) {
            OrgUnitPolicy p = policies.findByOrgUnitIdAndVersionId(currentId, versionId).orElse(null);
            if (p != null) {
                UUID v = pick.apply(p);
                if (v != null) return v;
            }
            OrgUnit u = units.findById(currentId).orElse(null);
            if (u == null) return null;
            currentId = u.getParentId();
            depth++;
        }
        return null;
    }

    private Map<String, Object> snapshot(OrgUnitPolicy p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("orgUnitId", p.getOrgUnitId());
        m.put("versionId", p.getVersionId());
        m.put("leaveGroupId", p.getLeaveGroupId());
        m.put("payrollGroupId", p.getPayrollGroupId());
        m.put("notes", p.getNotes());
        return m;
    }
}
