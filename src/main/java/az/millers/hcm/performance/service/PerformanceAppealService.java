package az.millers.hcm.performance.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.domain.PerformanceAppeal;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.repo.PerformanceAppealRepository;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_12 M396 — result acknowledgement (§25) + appeals (§26).
 *
 * <p>Acknowledgement is one-shot per review (with optional dispute). An appeal
 * requires a final rating and only one live appeal per review. An APPROVED
 * adjustment is applied through {@link PerfScoringService#override} so §37.12
 * holds: the original rating is preserved and the change is audited.
 */
@Service
public class PerformanceAppealService {

    private static final String MODULE = "PERFORMANCE";
    private static final List<String> LIVE_STATUSES = List.of("SUBMITTED", "UNDER_REVIEW", "RETURNED");

    private final PerformanceAppealRepository appeals;
    private final PerformanceReviewRepository reviews;
    private final PerfScoringService scoring;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final az.millers.hcm.corehr.repo.EmployeeRepository employees;
    private final az.millers.hcm.notifications.NotificationService notifications;

    public PerformanceAppealService(PerformanceAppealRepository appeals,
                                    PerformanceReviewRepository reviews,
                                    PerfScoringService scoring,
                                    AccessScopeService accessScope,
                                    AuditService audit,
                                    CurrentRequest currentRequest,
                                    az.millers.hcm.corehr.repo.EmployeeRepository employees,
                                    az.millers.hcm.notifications.NotificationService notifications) {
        this.appeals = appeals;
        this.reviews = reviews;
        this.scoring = scoring;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.employees = employees;
        this.notifications = notifications;
    }

    /** M402 — in-app note to the appellant; never fatal (§29). */
    private void notifyEmployee(UUID employeeId, String title, String body, UUID appealId) {
        try {
            employees.findById(employeeId)
                    .map(e -> e.getUsername())
                    .filter(u -> u != null && !u.isBlank())
                    .ifPresent(u -> notifications.notifyAll(u, title, body,
                            MODULE, "PerformanceAppeal", appealId.toString()));
        } catch (Exception ignored) {
            // notifications must never break the appeal flow
        }
    }

    // ── §25 acknowledgement ──────────────────────────────────────────────────

    @Transactional
    public PerformanceReview acknowledge(UUID reviewId, String comments, boolean disputed) {
        PerformanceReview r = requireAccessibleReview(reviewId);
        if (r.getAcknowledgedAt() != null) {
            throw new BadRequestException("This review is already acknowledged");
        }
        if (r.getFinalRating() == null) {
            throw new BadRequestException("There is no final result to acknowledge yet");
        }
        r.setAcknowledgedAt(OffsetDateTime.now());
        r.setAcknowledgedComments(comments);
        r.setAcknowledgementDisputed(disputed);
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, "PerformanceReview", reviewId.toString(), "ACKNOWLEDGE",
                null, Map.of("disputed", String.valueOf(disputed),
                        "comments", String.valueOf(comments)));
        return saved;
    }

    // ── §26 appeals ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PerformanceAppeal> list(UUID reviewId, String status) {
        List<PerformanceAppeal> rows;
        if (reviewId != null) {
            requireAccessibleReview(reviewId);
            rows = appeals.findByReviewIdOrderBySubmittedAtDesc(reviewId);
        } else if (status != null) {
            rows = appeals.findByTenantIdAndStatusOrderBySubmittedAtDesc(TenantContext.current(), status);
        } else {
            rows = appeals.findByTenantIdOrderBySubmittedAtDesc(TenantContext.current());
        }
        // GLOBAL RULE 7/8 — hide appeals for employees outside the caller's scope.
        return rows.stream().filter(a -> accessScope.isAccessible(a.getEmployeeId())).toList();
    }

    @Transactional
    public PerformanceAppeal submit(UUID reviewId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("An appeal reason is required");
        }
        PerformanceReview r = requireAccessibleReview(reviewId);
        if (r.getFinalRating() == null) {
            throw new BadRequestException("The review has no final rating to appeal");
        }
        if (appeals.existsByReviewIdAndStatusIn(reviewId, LIVE_STATUSES)) {
            throw new BadRequestException("An appeal is already in progress for this review");
        }
        PerformanceAppeal a = new PerformanceAppeal();
        a.setTenantId(r.getTenantId());
        a.setReviewId(reviewId);
        a.setEmployeeId(r.getEmployeeId());
        a.setReason(reason.trim());
        a.setOriginalRating(r.getFinalRating());
        a.setSubmittedBy(currentRequest.username());
        PerformanceAppeal saved = appeals.save(a);
        audit.record(MODULE, "PerformanceAppeal", saved.getId().toString(), "SUBMIT",
                null, Map.of("reviewId", reviewId.toString(), "reason", reason.trim()));
        return saved;
    }

    @Transactional
    public PerformanceAppeal takeUnderReview(UUID appealId) {
        PerformanceAppeal a = require(appealId);
        if (!"SUBMITTED".equals(a.getStatus()) && !"RETURNED".equals(a.getStatus())) {
            throw new BadRequestException("Only SUBMITTED/RETURNED appeals can be taken under review");
        }
        a.setStatus("UNDER_REVIEW");
        audit.record(MODULE, "PerformanceAppeal", appealId.toString(), "UNDER_REVIEW", null, null);
        return appeals.save(a);
    }

    /**
     * Decide the appeal: APPROVED (optionally adjusting the rating via the M394
     * override — original preserved §37.12), REJECTED, or RETURNED to the
     * employee for more information.
     */
    @Transactional
    public PerformanceAppeal decide(UUID appealId, String decision, BigDecimal adjustedRating,
                                    String notes) {
        PerformanceAppeal a = require(appealId);
        if (!"UNDER_REVIEW".equals(a.getStatus())) {
            throw new BadRequestException("Appeal must be UNDER_REVIEW to decide (was " + a.getStatus() + ")");
        }
        switch (decision) {
            case "APPROVED" -> {
                if (adjustedRating != null) {
                    // §37.12 — through the override path: original preserved + audited.
                    scoring.override(a.getReviewId(), adjustedRating,
                            "Appeal " + appealId + " approved: " + (notes == null ? a.getReason() : notes));
                    a.setAdjustedRating(adjustedRating);
                }
            }
            case "REJECTED", "RETURNED" -> { /* no rating change */ }
            default -> throw new BadRequestException(
                    "Decision must be APPROVED, REJECTED or RETURNED (was " + decision + ")");
        }
        a.setStatus(decision);
        a.setDecisionNotes(notes);
        a.setDecidedBy(currentRequest.username());
        a.setDecidedAt(OffsetDateTime.now());
        PerformanceAppeal saved = appeals.save(a);
        audit.record(MODULE, "PerformanceAppeal", appealId.toString(), "DECIDE",
                "UNDER_REVIEW", Map.of("decision", decision,
                        "adjustedRating", String.valueOf(adjustedRating),
                        "notes", String.valueOf(notes)));
        notifyEmployee(saved.getEmployeeId(), "Appeal " + decision.toLowerCase(),
                "Your performance appeal was " + decision.toLowerCase()
                        + (adjustedRating != null ? " — adjusted rating " + adjustedRating : "")
                        + (notes != null && !notes.isBlank() ? ". " + notes : "."),
                saved.getId());
        return saved;
    }

    /** The employee re-submits a RETURNED appeal with more information. */
    @Transactional
    public PerformanceAppeal resubmit(UUID appealId, String extraInfo) {
        PerformanceAppeal a = require(appealId);
        if (!"RETURNED".equals(a.getStatus())) {
            throw new BadRequestException("Only RETURNED appeals can be resubmitted");
        }
        if (extraInfo != null && !extraInfo.isBlank()) {
            a.setReason(a.getReason() + "\n---\n" + extraInfo.trim());
        }
        a.setStatus("SUBMITTED");
        audit.record(MODULE, "PerformanceAppeal", appealId.toString(), "RESUBMIT", "RETURNED", "SUBMITTED");
        return appeals.save(a);
    }

    @Transactional
    public PerformanceAppeal close(UUID appealId) {
        PerformanceAppeal a = require(appealId);
        if (!"APPROVED".equals(a.getStatus()) && !"REJECTED".equals(a.getStatus())) {
            throw new BadRequestException("Only decided (APPROVED/REJECTED) appeals can be closed");
        }
        a.setStatus("CLOSED");
        a.setClosedAt(OffsetDateTime.now());
        audit.record(MODULE, "PerformanceAppeal", appealId.toString(), "CLOSE", null, "CLOSED");
        return appeals.save(a);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PerformanceAppeal require(UUID id) {
        PerformanceAppeal a = appeals.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appeal not found: " + id));
        if (!accessScope.isAccessible(a.getEmployeeId())) {
            throw new AccessDeniedException("Appeal is outside your access scope");
        }
        return a;
    }

    private PerformanceReview requireAccessibleReview(UUID reviewId) {
        PerformanceReview r = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        if (!accessScope.isAccessible(r.getEmployeeId())) {
            throw new AccessDeniedException("Review is outside your access scope");
        }
        return r;
    }
}
