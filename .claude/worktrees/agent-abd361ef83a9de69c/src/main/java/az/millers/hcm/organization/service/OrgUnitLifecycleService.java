package az.millers.hcm.organization.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.api.dto.OrgUnitLifecycleDtos.ClosureRequest;
import az.millers.hcm.organization.api.dto.OrgUnitLifecycleDtos.ReopenRequest;
import az.millers.hcm.organization.api.dto.OrgUnitResponse;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.domain.OrgUnitHistory.ChangeKind;
import az.millers.hcm.organization.domain.OrgUnitLifecycleState;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M144 — lifecycle state machine for org units (§26).
 *
 * <p>State machine:
 * <pre>
 *  PLANNED ──open──► ACTIVE ──announce──► CLOSING ──close──► CLOSED
 *                      ▲                     │                  │
 *                      └──────cancel─────────┘                  │
 *                      └──────────────────reopen────────────────┘
 * </pre>
 *
 * <p>Lifecycle operations bypass the DRAFT-only constraint so they can be
 * applied to units in any structure version, including the ACTIVE one.
 */
@Service
public class OrgUnitLifecycleService {

    private static final String MODULE = "ORGANIZATION";
    private static final String ENTITY = "OrgUnit";

    private final OrgUnitRepository repo;
    private final OrgUnitHistoryService history;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OrgUnitLifecycleService(OrgUnitRepository repo,
                                    OrgUnitHistoryService history,
                                    AuditService audit,
                                    CurrentRequest currentRequest) {
        this.repo = repo;
        this.history = history;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /** PLANNED → ACTIVE. */
    @Transactional
    public OrgUnit open(UUID unitId) {
        OrgUnit u = get(unitId);
        requireState(u, OrgUnitLifecycleState.PLANNED,
                "open", OrgUnitLifecycleState.PLANNED);
        return transition(u, OrgUnitLifecycleState.ACTIVE, null, null);
    }

    /** ACTIVE → CLOSING. Records the announced date and reason. */
    @Transactional
    public OrgUnit announceClosure(UUID unitId, ClosureRequest req) {
        OrgUnit u = get(unitId);
        requireState(u, OrgUnitLifecycleState.ACTIVE,
                "announce-closure", OrgUnitLifecycleState.ACTIVE);
        OrgUnitResponse before = OrgUnitResponse.from(u);
        u.setClosureAnnouncedDate(req.effectiveDate() != null ? req.effectiveDate() : LocalDate.now());
        u.setClosureReason(req.reason());
        return transition(u, OrgUnitLifecycleState.CLOSING, before, req.reason());
    }

    /** CLOSING → ACTIVE. Clears the closure dates and reason. */
    @Transactional
    public OrgUnit cancelClosure(UUID unitId) {
        OrgUnit u = get(unitId);
        requireState(u, OrgUnitLifecycleState.CLOSING,
                "cancel-closure", OrgUnitLifecycleState.CLOSING);
        OrgUnitResponse before = OrgUnitResponse.from(u);
        u.setClosureAnnouncedDate(null);
        u.setClosureReason(null);
        return transition(u, OrgUnitLifecycleState.ACTIVE, before, "Closure cancelled");
    }

    /** CLOSING or ACTIVE → CLOSED. Records closed date and by-whom. */
    @Transactional
    public OrgUnit close(UUID unitId, ClosureRequest req) {
        OrgUnit u = get(unitId);
        if (u.getLifecycleState() != OrgUnitLifecycleState.CLOSING
                && u.getLifecycleState() != OrgUnitLifecycleState.ACTIVE) {
            throw new BadRequestException(
                    "close requires ACTIVE or CLOSING state; current: " + u.getLifecycleState());
        }
        OrgUnitResponse before = OrgUnitResponse.from(u);
        u.setClosedDate(req.effectiveDate() != null ? req.effectiveDate() : LocalDate.now());
        u.setClosedBy(currentRequest.username());
        if (req.reason() != null) u.setClosureReason(req.reason());
        return transition(u, OrgUnitLifecycleState.CLOSED, before, req.reason());
    }

    /** CLOSED → ACTIVE. Intended for error correction or reopening a unit. */
    @Transactional
    public OrgUnit reopen(UUID unitId, ReopenRequest req) {
        OrgUnit u = get(unitId);
        requireState(u, OrgUnitLifecycleState.CLOSED,
                "reopen", OrgUnitLifecycleState.CLOSED);
        OrgUnitResponse before = OrgUnitResponse.from(u);
        u.setClosedDate(null);
        u.setClosedBy(null);
        u.setClosureReason(null);
        u.setClosureAnnouncedDate(null);
        return transition(u, OrgUnitLifecycleState.ACTIVE, before,
                req != null ? req.reason() : null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private OrgUnit get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Org unit not found: " + id));
    }

    private void requireState(OrgUnit u, OrgUnitLifecycleState required,
                               String op, OrgUnitLifecycleState... allowed) {
        if (u.getLifecycleState() != required) {
            throw new BadRequestException(
                    op + " requires " + required + " state; current: " + u.getLifecycleState());
        }
    }

    private OrgUnit transition(OrgUnit u, OrgUnitLifecycleState to,
                                Object before, String reason) {
        OrgUnitResponse snapshot = before instanceof OrgUnitResponse r ? r : OrgUnitResponse.from(u);
        u.setLifecycleState(to);
        // Keep active flag in sync: only CLOSED means active=false.
        u.setActive(to != OrgUnitLifecycleState.CLOSED);
        OrgUnit saved = repo.save(u);
        OrgUnitResponse after = OrgUnitResponse.from(saved);
        history.record(saved.getId(), saved.getVersionId(),
                ChangeKind.LIFECYCLE_CHANGE, snapshot, after, reason);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "LIFECYCLE_" + to.name(), snapshot, after);
        return saved;
    }
}
