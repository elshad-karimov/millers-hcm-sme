package az.millers.hcm.leave.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.leave.api.dto.LeaveGroupEntitlementRequest;
import az.millers.hcm.leave.api.dto.LeaveGroupEntitlementResponse;
import az.millers.hcm.leave.api.dto.LeaveGroupRequest;
import az.millers.hcm.leave.api.dto.LeaveGroupResponse;
import az.millers.hcm.leave.domain.LeaveGroup;
import az.millers.hcm.leave.domain.LeaveGroupEntitlement;
import az.millers.hcm.leave.repo.LeaveGroupEntitlementRepository;
import az.millers.hcm.leave.repo.LeaveGroupRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Manages {@link LeaveGroup}s and their per-({@link az.millers.hcm.leave.domain.LeaveType})
 * entitlement overrides (M66 / P1-08).
 *
 * <p>Read-side helper {@link #resolveEntitlementSource(UUID, UUID)} is the
 * one method {@code LeaveAccrualService} consults. It returns the override row
 * if one exists for {@code (group, type)}, falling through to {@code null}
 * (meaning "use LeaveType defaults") otherwise. Keeping this resolution in the
 * group service — rather than re-implementing it in the accrual walker — means
 * any future override pathway (group inheritance, time-bounded overrides, etc.)
 * lands in one place.
 */
@Service
public class LeaveGroupService {

    private static final String MODULE = "LEAVE";
    private static final String GROUP_ENTITY = "LeaveGroup";
    private static final String ENTITLEMENT_ENTITY = "LeaveGroupEntitlement";

    private final LeaveGroupRepository groups;
    private final LeaveGroupEntitlementRepository entitlements;
    private final LeaveTypeRepository leaveTypes;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LeaveGroupService(LeaveGroupRepository groups,
                              LeaveGroupEntitlementRepository entitlements,
                              LeaveTypeRepository leaveTypes,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.groups = groups;
        this.entitlements = entitlements;
        this.leaveTypes = leaveTypes;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Group CRUD ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LeaveGroupResponse> list() {
        return groups.findByActiveTrueOrderByNameAsc()
                .stream().map(LeaveGroupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LeaveGroupResponse get(UUID id) {
        return LeaveGroupResponse.from(loadGroup(id));
    }

    @Transactional
    public LeaveGroupResponse create(LeaveGroupRequest req) {
        if (groups.findByCode(req.code()).isPresent()) {
            throw new BadRequestException("A leave group with code '" + req.code() + "' already exists");
        }
        LeaveGroup g = new LeaveGroup();
        g.setCode(req.code());
        g.setName(req.name());
        g.setDescription(req.description());
        g.setActive(req.active() == null || req.active());
        g.setCreatedBy(currentRequest.username());
        g.setUpdatedBy(currentRequest.username());
        if (Boolean.TRUE.equals(req.defaultGroup())) {
            promoteToDefault(g);
        }
        LeaveGroup saved = groups.save(g);
        audit.record(MODULE, GROUP_ENTITY, saved.getId().toString(),
                "CREATE", null, LeaveGroupResponse.from(saved));
        return LeaveGroupResponse.from(saved);
    }

    @Transactional
    public LeaveGroupResponse update(UUID id, LeaveGroupRequest req) {
        LeaveGroup g = loadGroup(id);
        LeaveGroupResponse before = LeaveGroupResponse.from(g);
        if (!g.getCode().equals(req.code()) && groups.findByCode(req.code()).isPresent()) {
            throw new BadRequestException("A leave group with code '" + req.code() + "' already exists");
        }
        g.setCode(req.code());
        g.setName(req.name());
        g.setDescription(req.description());
        if (req.active() != null) g.setActive(req.active());
        g.setUpdatedBy(currentRequest.username());
        if (Boolean.TRUE.equals(req.defaultGroup()) && !g.isDefaultGroup()) {
            promoteToDefault(g);
        }
        LeaveGroup saved = groups.save(g);
        audit.record(MODULE, GROUP_ENTITY, id.toString(),
                "UPDATE", before, LeaveGroupResponse.from(saved));
        return LeaveGroupResponse.from(saved);
    }

    /**
     * Demote any currently-default group and promote {@code candidate} in the
     * same transaction. The partial unique index {@code uq_leave_group_default}
     * in V54 enforces "at most one default" at the DB level so the swap must
     * happen inside one TX with an explicit flush.
     */
    private void promoteToDefault(LeaveGroup candidate) {
        groups.findFirstByDefaultGroupTrue().ifPresent(existing -> {
            if (!existing.getId().equals(candidate.getId())) {
                existing.setDefaultGroup(false);
                existing.setUpdatedBy(currentRequest.username());
                groups.save(existing);
                groups.flush();
            }
        });
        candidate.setDefaultGroup(true);
    }

    // ── Per-(group, type) entitlement overrides ───────────────────────────────

    @Transactional(readOnly = true)
    public List<LeaveGroupEntitlementResponse> listEntitlements(UUID groupId) {
        loadGroup(groupId); // existence + scope check
        return entitlements.findByLeaveGroupId(groupId)
                .stream().map(LeaveGroupEntitlementResponse::from).toList();
    }

    @Transactional
    public LeaveGroupEntitlementResponse upsertEntitlement(UUID groupId,
                                                            LeaveGroupEntitlementRequest req) {
        LeaveGroup g = loadGroup(groupId);
        if (!leaveTypes.existsById(req.leaveTypeId())) {
            throw new BadRequestException("Leave type not found: " + req.leaveTypeId());
        }

        LeaveGroupEntitlement e = entitlements
                .findByLeaveGroupIdAndLeaveTypeId(g.getId(), req.leaveTypeId())
                .orElseGet(LeaveGroupEntitlement::new);
        boolean isNew = e.getId() == null;
        LeaveGroupEntitlementResponse before = isNew ? null : LeaveGroupEntitlementResponse.from(e);

        e.setLeaveGroupId(g.getId());
        e.setLeaveTypeId(req.leaveTypeId());
        e.setAnnualEntitlementDays(req.annualEntitlementDays());
        e.setMonthlyAccrualDays(req.monthlyAccrualDays());
        e.setSeniorityBrackets(req.seniorityBrackets());
        e.setNotes(req.notes());
        if (isNew) {
            e.setCreatedBy(currentRequest.username());
        }
        e.setUpdatedBy(currentRequest.username());
        LeaveGroupEntitlement saved = entitlements.save(e);

        audit.record(MODULE, ENTITLEMENT_ENTITY, saved.getId().toString(),
                isNew ? "CREATE" : "UPDATE",
                before, LeaveGroupEntitlementResponse.from(saved));
        return LeaveGroupEntitlementResponse.from(saved);
    }

    @Transactional
    public void deleteEntitlement(UUID groupId, UUID leaveTypeId) {
        entitlements.findByLeaveGroupIdAndLeaveTypeId(groupId, leaveTypeId).ifPresent(e -> {
            LeaveGroupEntitlementResponse before = LeaveGroupEntitlementResponse.from(e);
            entitlements.delete(e);
            audit.record(MODULE, ENTITLEMENT_ENTITY, e.getId().toString(),
                    "DELETE", before, null);
        });
    }

    // ── Read-side helper consumed by LeaveAccrualService ──────────────────────

    /**
     * Returns the override entitlement for ({@code leaveGroupId}, {@code leaveTypeId})
     * if one exists. Returns {@code null} when:
     * <ul>
     *   <li>{@code leaveGroupId} is {@code null} (employee is in the implicit
     *       default group — but if a row exists for the explicit default group +
     *       this type, that takes precedence; see overload below)</li>
     *   <li>no override row exists for this pair</li>
     * </ul>
     *
     * <p>Caller falls through to the LeaveType's own defaults when this returns
     * null — that's the whole point of the override design.
     */
    @Transactional(readOnly = true)
    public LeaveGroupEntitlement resolveEntitlementSource(UUID leaveGroupId, UUID leaveTypeId) {
        UUID resolvedGroupId = leaveGroupId;
        if (resolvedGroupId == null) {
            // Fall through to the default group (if any has been marked default).
            resolvedGroupId = groups.findFirstByDefaultGroupTrue()
                    .map(LeaveGroup::getId)
                    .orElse(null);
        }
        if (resolvedGroupId == null) return null;
        return entitlements
                .findByLeaveGroupIdAndLeaveTypeId(resolvedGroupId, leaveTypeId)
                .orElse(null);
    }

    private LeaveGroup loadGroup(UUID id) {
        return groups.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Leave group not found: " + id));
    }
}
