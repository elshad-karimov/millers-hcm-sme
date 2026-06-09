package az.millers.hcm.staffing.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionLifecycleEvent;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.domain.VacancyState;
import az.millers.hcm.staffing.repo.PositionLifecycleEventRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M243 — Phase A of the Position Management spec.
 *
 * <p>One central service that owns every state transition on
 * {@link PositionStatus}. Each public action — submit / approve /
 * reject / activate / freeze / unfreeze / mark-under-review /
 * finish-review / close / archive — is a thin wrapper around a single
 * {@link #transition} primitive so the validation, breadcrumb update,
 * event-journal write and audit call all live in ONE place. Per the
 * standing "develop once, use everywhere" rule.
 */
@Service
public class PositionLifecycleService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "Position";

    /**
     * Allowed transition matrix. {@code from → set of legal to-states}.
     * Anything not in the set is rejected with a 400.
     */
    private static final Map<PositionStatus, EnumSet<PositionStatus>> ALLOWED;
    static {
        ALLOWED = new EnumMap<>(PositionStatus.class);
        ALLOWED.put(PositionStatus.DRAFT,             EnumSet.of(PositionStatus.PENDING_APPROVAL, PositionStatus.CLOSED));
        ALLOWED.put(PositionStatus.PENDING_APPROVAL,  EnumSet.of(PositionStatus.APPROVED, PositionStatus.DRAFT, PositionStatus.CLOSED));
        ALLOWED.put(PositionStatus.APPROVED,          EnumSet.of(PositionStatus.ACTIVE, PositionStatus.CLOSED));
        ALLOWED.put(PositionStatus.ACTIVE,            EnumSet.of(PositionStatus.FROZEN, PositionStatus.UNDER_REVIEW, PositionStatus.CLOSED));
        ALLOWED.put(PositionStatus.FROZEN,            EnumSet.of(PositionStatus.ACTIVE, PositionStatus.CLOSED));
        ALLOWED.put(PositionStatus.UNDER_REVIEW,      EnumSet.of(PositionStatus.ACTIVE, PositionStatus.CLOSED));
        ALLOWED.put(PositionStatus.CLOSED,            EnumSet.of(PositionStatus.ARCHIVED));
        ALLOWED.put(PositionStatus.ARCHIVED,          EnumSet.noneOf(PositionStatus.class));
    }

    private final PositionRepository positions;
    private final PositionLifecycleEventRepository events;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PositionLifecycleService(PositionRepository positions,
                                     PositionLifecycleEventRepository events,
                                     AuditService audit,
                                     CurrentRequest currentRequest) {
        this.positions = positions;
        this.events = events;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Public actions (one per state transition) ──────────────────────────

    @Transactional
    public Position submitForApproval(UUID id, String comments) {
        return transition(id, PositionStatus.PENDING_APPROVAL, null, comments, null);
    }

    @Transactional
    public Position approve(UUID id, String comments) {
        return transition(id, PositionStatus.APPROVED, null, comments, null);
    }

    /** Sends back to DRAFT; reason is required so the submitter knows why. */
    @Transactional
    public Position reject(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Rejection reason is required");
        }
        return transition(id, PositionStatus.DRAFT, reason, null, null);
    }

    @Transactional
    public Position activate(UUID id) {
        return transition(id, PositionStatus.ACTIVE, null, null, null);
    }

    /**
     * Reason required; {@code scheduledUnfreezeDate} is optional — when set,
     * a future job can auto-unfreeze (out of scope for M243; field stored
     * for the M244 scheduler).
     */
    @Transactional
    public Position freeze(UUID id, String reason, LocalDate scheduledUnfreezeDate, String comments) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Freeze reason is required");
        }
        return transition(id, PositionStatus.FROZEN, reason, comments, scheduledUnfreezeDate);
    }

    @Transactional
    public Position unfreeze(UUID id, String comments) {
        return transition(id, PositionStatus.ACTIVE, null, comments, null);
    }

    @Transactional
    public Position markUnderReview(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Review reason is required");
        }
        return transition(id, PositionStatus.UNDER_REVIEW, reason, null, null);
    }

    @Transactional
    public Position finishReview(UUID id, String comments) {
        return transition(id, PositionStatus.ACTIVE, null, comments, null);
    }

    /** Reason required; pre-existing StaffingService.close() now delegates here. */
    @Transactional
    public Position close(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Closure reason is required");
        }
        Position p = loadOrThrow(id);
        if (p.getOccupiedHeadcount() > 0) {
            throw new BadRequestException("Cannot close a position with occupied headcount — reassign occupants first");
        }
        return transition(id, PositionStatus.CLOSED, reason, null, null);
    }

    @Transactional
    public Position archive(UUID id) {
        return transition(id, PositionStatus.ARCHIVED, null, null, null);
    }

    // ── Read ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PositionLifecycleEvent> history(UUID positionId) {
        // Confirm the position exists before returning history; surfaces a
        // 404 instead of an empty list when the caller hits a stale ID.
        loadOrThrow(positionId);
        return events.findByPositionIdOrderByOccurredAtDesc(positionId);
    }

    // ── The single transition primitive — every public action goes here ──

    private Position transition(UUID id,
                                 PositionStatus to,
                                 String reason,
                                 String comments,
                                 LocalDate scheduledUnfreezeDate) {
        Position p = loadOrThrow(id);
        PositionStatus from = p.getStatus();

        EnumSet<PositionStatus> legal = ALLOWED.getOrDefault(from, EnumSet.noneOf(PositionStatus.class));
        if (!legal.contains(to)) {
            throw new BadRequestException(
                    "Position transition " + from + " → " + to + " is not allowed");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String actor = currentRequest.username();

        // Update breadcrumb columns by destination state so the row can
        // answer "why" without joining the event journal.
        applyBreadcrumbs(p, to, reason, scheduledUnfreezeDate, actor, now);

        p.setStatus(to);
        p.setUpdatedBy(actor);
        Position saved = positions.save(p);

        // Reset vacancy-state side-effects where lifecycle should drive
        // them (e.g. CLOSED ⇒ vacancyState=CANCELLED).
        if (to == PositionStatus.CLOSED) {
            saved.setVacancyState(VacancyState.CANCELLED);
            saved = positions.save(saved);
        } else if (to == PositionStatus.FROZEN) {
            saved.setVacancyState(VacancyState.FROZEN);
            saved = positions.save(saved);
        } else if (from == PositionStatus.FROZEN && to == PositionStatus.ACTIVE) {
            // Unfreezing: recompute vacancy state from current occupancy.
            saved.setVacancyState(deriveVacancyFromOccupancy(saved));
            saved = positions.save(saved);
        }

        PositionLifecycleEvent ev = new PositionLifecycleEvent();
        ev.setPositionId(id);
        ev.setFromStatus(from);
        ev.setToStatus(to);
        ev.setReason(reason);
        ev.setComments(comments);
        ev.setScheduledUnfreezeDate(scheduledUnfreezeDate);
        ev.setActor(actor);
        ev.setOccurredAt(now);
        events.save(ev);

        audit.record(MODULE, ENTITY, id.toString(),
                "LIFECYCLE_" + to.name(),
                new LifecycleSnapshot(from, null, null),
                new LifecycleSnapshot(to, reason, scheduledUnfreezeDate));
        return saved;
    }

    private void applyBreadcrumbs(Position p,
                                   PositionStatus to,
                                   String reason,
                                   LocalDate scheduledUnfreezeDate,
                                   String actor,
                                   OffsetDateTime now) {
        switch (to) {
            case APPROVED -> {
                p.setApprovedAt(now);
                p.setApprovedBy(actor);
            }
            case FROZEN -> {
                p.setFreezeReason(reason);
                p.setFrozenAt(now);
                p.setFrozenBy(actor);
                p.setScheduledUnfreezeDate(scheduledUnfreezeDate);
            }
            case ACTIVE -> {
                // Clear freeze/review breadcrumbs on transition back to ACTIVE.
                p.setFreezeReason(null);
                p.setFrozenAt(null);
                p.setFrozenBy(null);
                p.setScheduledUnfreezeDate(null);
                p.setReviewReason(null);
                p.setUnderReviewAt(null);
                p.setUnderReviewBy(null);
            }
            case UNDER_REVIEW -> {
                p.setReviewReason(reason);
                p.setUnderReviewAt(now);
                p.setUnderReviewBy(actor);
            }
            case CLOSED -> {
                p.setClosureReason(reason);
                p.setClosedAt(now);
                p.setClosedBy(actor);
            }
            default -> { /* DRAFT, PENDING_APPROVAL, ARCHIVED — no breadcrumbs */ }
        }
    }

    private VacancyState deriveVacancyFromOccupancy(Position p) {
        int occupied = p.getOccupiedHeadcount();
        int approved = p.getApprovedHeadcount();
        if (occupied >= approved && approved > 0) return VacancyState.OCCUPIED;
        if (occupied > 0) return VacancyState.PARTIALLY_OCCUPIED;
        return VacancyState.VACANT;
    }

    private Position loadOrThrow(UUID id) {
        return positions.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Position not found: " + id));
    }

    /** Snapshot record for the audit log. */
    public record LifecycleSnapshot(PositionStatus status, String reason, LocalDate scheduledUnfreezeDate) {}
}
