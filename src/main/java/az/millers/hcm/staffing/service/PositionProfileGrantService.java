package az.millers.hcm.staffing.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.GrantStatus;
import az.millers.hcm.staffing.domain.PositionOccupancy;
import az.millers.hcm.staffing.domain.PositionProfileGrant;
import az.millers.hcm.staffing.domain.PositionProfileItem;
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

    public PositionProfileGrantService(PositionProfileGrantRepository grants,
                                        PositionProfileItemRepository items,
                                        AuditService audit,
                                        CurrentRequest currentRequest) {
        this.grants = grants;
        this.items = items;
        this.audit = audit;
        this.currentRequest = currentRequest;
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
            created.add(grants.save(g));
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
}
