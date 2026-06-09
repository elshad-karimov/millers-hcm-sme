package az.millers.hcm.staffing.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.EmployeeAllowanceRequest;
import az.millers.hcm.compbenefits.domain.AllowanceType;
import az.millers.hcm.compbenefits.domain.EmployeeAllowance;
import az.millers.hcm.compbenefits.repo.AllowanceTypeRepository;
import az.millers.hcm.compbenefits.service.EmployeeAllowanceService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.GrantStatus;
import az.millers.hcm.staffing.domain.PositionOccupancy;
import az.millers.hcm.staffing.domain.PositionProfileGrant;
import az.millers.hcm.staffing.domain.PositionProfileItem;
import az.millers.hcm.staffing.domain.ProfileItemType;
import az.millers.hcm.staffing.repo.PositionProfileGrantRepository;
import az.millers.hcm.staffing.repo.PositionProfileItemRepository;

/**
 * M250 — Phase F.2: grant lifecycle on a position profile.
 *
 * <p>Owns every write path on {@link PositionProfileGrant}. The main
 * entry point is {@link #autoGrantForOccupancy} — called by
 * {@link PositionOccupancyService} when a PRIMARY occupancy is created.
 * For each mandatory profile item on the position, this service writes
 * a PENDING grant row so HR sees the to-do list immediately.
 */
@Service
public class PositionProfileGrantService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "PositionProfileGrant";

    private final PositionProfileGrantRepository grants;
    private final PositionProfileItemRepository items;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    // M251 — Phase F.3: when an ALLOWANCE grant has a reference_code that
    // resolves to an AllowanceType.code, we auto-create the matching
    // employee_allowance row so payroll picks it up immediately.
    private final EmployeeAllowanceService employeeAllowanceService;
    private final AllowanceTypeRepository allowanceTypes;

    public PositionProfileGrantService(PositionProfileGrantRepository grants,
                                        PositionProfileItemRepository items,
                                        AuditService audit,
                                        CurrentRequest currentRequest,
                                        EmployeeAllowanceService employeeAllowanceService,
                                        AllowanceTypeRepository allowanceTypes) {
        this.grants = grants;
        this.items = items;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.employeeAllowanceService = employeeAllowanceService;
        this.allowanceTypes = allowanceTypes;
    }

    // ── Reads ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PositionProfileGrant> forOccupancy(UUID occupancyId) {
        return grants.findByOccupancyIdOrderByItemTypeAscLabelAsc(occupancyId);
    }

    @Transactional(readOnly = true)
    public List<PositionProfileGrant> pendingForEmployee(UUID employeeId) {
        return grants.findByEmployeeIdAndStatusOrderByCreatedAtDesc(employeeId, GrantStatus.PENDING);
    }

    // ── Auto-grant on occupancy create (called by PositionOccupancyService) ──

    /**
     * Create PENDING grants for every mandatory profile item on the
     * position. Idempotent — if the occupancy already has a grant for a
     * profile item, it isn't recreated. Safe to call on M249's
     * {@code openPrimary} re-run paths.
     */
    @Transactional
    public List<PositionProfileGrant> autoGrantForOccupancy(PositionOccupancy occupancy) {
        if (occupancy == null) return List.of();
        var profileItems = items.findByPositionIdOrderByItemTypeAscSortOrderAscLabelAsc(
                occupancy.getPositionId());
        var existing = grants.findByOccupancyIdOrderByItemTypeAscLabelAsc(occupancy.getId());
        var existingItemIds = existing.stream()
                .map(PositionProfileGrant::getProfileItemId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.List<PositionProfileGrant> created = new java.util.ArrayList<>();
        String actor = currentRequest.username();
        for (PositionProfileItem it : profileItems) {
            if (!it.isMandatory()) continue;          // optional items not auto-granted
            if (existingItemIds.contains(it.getId())) continue;  // idempotent

            PositionProfileGrant g = new PositionProfileGrant();
            g.setOccupancyId(occupancy.getId());
            g.setProfileItemId(it.getId());
            g.setEmployeeId(occupancy.getEmployeeId());
            g.setPositionId(occupancy.getPositionId());
            // Snapshot — copy at grant time.
            g.setItemType(it.getItemType());
            g.setLabel(it.getLabel());
            g.setValueAmount(it.getValueAmount());
            g.setCurrency(it.getCurrency());
            g.setReferenceCode(it.getReferenceCode());
            g.setNotes(it.getNotes());
            g.setStatus(GrantStatus.PENDING);
            g.setCreatedBy(actor);
            g.setUpdatedBy(actor);
            PositionProfileGrant saved = grants.save(g);

            // M251 — Phase F.3: for ALLOWANCE grants with a non-blank
            // reference_code matching an AllowanceType.code, immediately
            // create the employee_allowance row + flip the grant to
            // ACTIVE. Soft-fail if anything goes wrong so a single bad
            // allowance code doesn't take down the whole hire.
            saved = tryAutoFireAllowance(saved, occupancy);
            created.add(saved);
        }

        if (!created.isEmpty()) {
            audit.record(MODULE, ENTITY, occupancy.getId().toString(),
                    "AUTO_GRANT",
                    null,
                    java.util.Map.of(
                            "createdGrants", created.size(),
                            "employeeId", occupancy.getEmployeeId().toString(),
                            "positionId", occupancy.getPositionId().toString()));
        }
        return created;
    }

    // ── Operator transitions ──────────────────────────────────────────────

    /** Operator marks PENDING grant as ACTIVE — the underlying work is done. */
    @Transactional
    public PositionProfileGrant markActive(UUID grantId) {
        PositionProfileGrant g = loadOrThrow(grantId);
        if (g.getStatus() == GrantStatus.ACTIVE) return g;
        if (g.getStatus() != GrantStatus.PENDING && g.getStatus() != GrantStatus.FAILED) {
            throw new BadRequestException(
                    "Can only mark PENDING/FAILED grants as ACTIVE; current = " + g.getStatus());
        }
        // M251 — Phase F.3: if this is an ALLOWANCE that wasn't auto-fired
        // on hire (no downstream row yet), try to create it now. Otherwise
        // just flip the status — operator confirms the work is done.
        if (g.getItemType() == ProfileItemType.ALLOWANCE
                && g.getDownstreamEntityId() == null
                && hasText(g.getReferenceCode())) {
            return tryFireAllowanceForExistingGrant(g);
        }
        g.setStatus(GrantStatus.ACTIVE);
        g.setGrantedAt(OffsetDateTime.now());
        g.setGrantedBy(currentRequest.username());
        g.setUpdatedBy(currentRequest.username());
        return grants.save(g);
    }

    /**
     * Operator revokes a grant. Used both manually and via
     * {@link #revokeAllForOccupancy} when the occupancy ends.
     */
    @Transactional
    public PositionProfileGrant revoke(UUID grantId, String reason) {
        PositionProfileGrant g = loadOrThrow(grantId);
        if (g.getStatus() == GrantStatus.REVOKED) return g;
        // M251 — Phase F.3: if this grant has a downstream allowance row,
        // end it now so payroll stops picking it up.
        tryEndDownstreamAllowance(g);
        g.setStatus(GrantStatus.REVOKED);
        g.setRevokedAt(OffsetDateTime.now());
        g.setRevokedBy(currentRequest.username());
        g.setRevokeReason(reason);
        g.setUpdatedBy(currentRequest.username());
        return grants.save(g);
    }

    /**
     * Bulk-revoke every non-terminal grant for an occupancy. Called by
     * {@link PositionOccupancyService#end} so ending an occupancy also
     * pulls all the associated grants.
     */
    @Transactional
    public int revokeAllForOccupancy(UUID occupancyId, String reason) {
        var rows = grants.findByOccupancyIdOrderByItemTypeAscLabelAsc(occupancyId);
        int touched = 0;
        OffsetDateTime now = OffsetDateTime.now();
        String actor = currentRequest.username();
        for (PositionProfileGrant g : rows) {
            if (g.getStatus().isTerminal()) continue;
            // M251 — end the linked employee_allowance (if any) so
            // payroll stops picking it up. Soft-fail per row so one
            // dangling allowance doesn't break the whole revoke.
            tryEndDownstreamAllowance(g);
            g.setStatus(GrantStatus.REVOKED);
            g.setRevokedAt(now);
            g.setRevokedBy(actor);
            g.setRevokeReason(reason);
            g.setUpdatedBy(actor);
            grants.save(g);
            touched++;
        }
        if (touched > 0) {
            audit.record(MODULE, ENTITY, occupancyId.toString(),
                    "BULK_REVOKE",
                    null,
                    java.util.Map.of("revokedCount", touched,
                            "reason", reason == null ? "" : reason));
        }
        return touched;
    }

    /** Operator marks a grant FAILED (cross-module integration error). */
    @Transactional
    public PositionProfileGrant markFailed(UUID grantId, String reason) {
        PositionProfileGrant g = loadOrThrow(grantId);
        g.setStatus(GrantStatus.FAILED);
        g.setFailureReason(reason);
        g.setUpdatedBy(currentRequest.username());
        return grants.save(g);
    }

    private PositionProfileGrant loadOrThrow(UUID id) {
        return grants.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Grant not found: " + id));
    }

    // ── M251 — Phase F.3: ALLOWANCE wire-up ───────────────────────────────

    /**
     * Auto-fire path for grants created during {@link #autoGrantForOccupancy}.
     * Returns the grant in its final state — either ACTIVE (allowance
     * created, downstream id stashed) or FAILED (reference_code didn't
     * resolve, or downstream service threw).
     */
    private PositionProfileGrant tryAutoFireAllowance(PositionProfileGrant g, PositionOccupancy occ) {
        if (g.getItemType() != ProfileItemType.ALLOWANCE) return g;
        if (!hasText(g.getReferenceCode())) return g;            // no code → leave PENDING
        if (g.getValueAmount() == null) return markFailedSilently(g,
                "Allowance grant has no amount; cannot fire downstream");

        java.util.Optional<AllowanceType> typeOpt = allowanceTypes.findByCode(g.getReferenceCode());
        if (typeOpt.isEmpty()) {
            return markFailedSilently(g,
                    "No allowance_type with code '" + g.getReferenceCode() + "'");
        }
        return createDownstreamAllowance(g, typeOpt.get(), occ.getStartDate());
    }

    /**
     * Operator-triggered late-fire path called from {@link #markActive}
     * when an ALLOWANCE grant didn't auto-fire on hire (e.g. the
     * AllowanceType was added after the grant was created).
     */
    private PositionProfileGrant tryFireAllowanceForExistingGrant(PositionProfileGrant g) {
        java.util.Optional<AllowanceType> typeOpt = allowanceTypes.findByCode(g.getReferenceCode());
        if (typeOpt.isEmpty()) {
            throw new BadRequestException(
                    "No allowance_type with code '" + g.getReferenceCode() + "'");
        }
        if (g.getValueAmount() == null) {
            throw new BadRequestException("Allowance grant has no amount; cannot fire downstream");
        }
        return createDownstreamAllowance(g, typeOpt.get(), java.time.LocalDate.now());
    }

    /**
     * Build + save the EmployeeAllowanceRequest, then stash the resulting
     * row id back into the grant. Caller picks the {@code effectiveFrom}
     * (hire date for auto-fire; today for operator late-fire).
     */
    private PositionProfileGrant createDownstreamAllowance(
            PositionProfileGrant g,
            AllowanceType type,
            java.time.LocalDate effectiveFrom) {
        try {
            var req = new EmployeeAllowanceRequest(
                    g.getEmployeeId(),
                    type.getId(),
                    g.getValueAmount(),
                    hasText(g.getCurrency()) ? g.getCurrency() : type.getCurrency(),
                    effectiveFrom == null ? java.time.LocalDate.now() : effectiveFrom,
                    null,  // open-ended; ended on revoke
                    "Auto-granted from position profile (M251)");
            EmployeeAllowance created = employeeAllowanceService.create(req);

            g.setStatus(GrantStatus.ACTIVE);
            g.setGrantedAt(OffsetDateTime.now());
            g.setGrantedBy(currentRequest.username());
            g.setDownstreamEntityId(created.getId());
            g.setDownstreamEntityType("EMPLOYEE_ALLOWANCE");
            g.setUpdatedBy(currentRequest.username());
            return grants.save(g);
        } catch (RuntimeException ex) {
            return markFailedSilently(g, "Downstream allowance create failed: " + ex.getMessage());
        }
    }

    /**
     * End the linked {@code employee_allowance} row when a grant is
     * revoked. Soft-fail — log the issue on the grant but don't throw
     * so the revoke itself completes.
     */
    private void tryEndDownstreamAllowance(PositionProfileGrant g) {
        if (g.getDownstreamEntityId() == null) return;
        if (!"EMPLOYEE_ALLOWANCE".equals(g.getDownstreamEntityType())) return;
        try {
            employeeAllowanceService.end(g.getDownstreamEntityId(), java.time.LocalDate.now());
        } catch (RuntimeException ex) {
            // Stash the failure reason on the grant but don't block the revoke.
            g.setFailureReason("Downstream allowance end failed: " + ex.getMessage());
        }
    }

    private PositionProfileGrant markFailedSilently(PositionProfileGrant g, String reason) {
        g.setStatus(GrantStatus.FAILED);
        g.setFailureReason(reason);
        g.setUpdatedBy(currentRequest.username());
        return grants.save(g);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
