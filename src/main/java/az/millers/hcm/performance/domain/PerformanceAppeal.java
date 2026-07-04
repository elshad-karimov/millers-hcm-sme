package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HCM_12 M396 — appeal against a review result (PRD §26).
 * SUBMITTED → UNDER_REVIEW → APPROVED / REJECTED / RETURNED → CLOSED.
 * An APPROVED adjustment goes through the M394 override path (§37.12 —
 * the original rating is preserved, never overwritten).
 */
@Entity
@Table(name = "performance_appeal", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class PerformanceAppeal {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false, length = 2000)
    private String reason;

    /** SUBMITTED | UNDER_REVIEW | APPROVED | REJECTED | RETURNED | CLOSED */
    @Column(nullable = false, length = 20)
    private String status = "SUBMITTED";

    /** Snapshot of the review's final rating at appeal time. */
    @Column(name = "original_rating", precision = 6, scale = 3)
    private BigDecimal originalRating;

    /** Set when the appeal is APPROVED with a rating adjustment. */
    @Column(name = "adjusted_rating", precision = 6, scale = 3)
    private BigDecimal adjustedRating;

    @Column(name = "decision_notes", length = 2000)
    private String decisionNotes;

    @Column(name = "submitted_by", length = 80)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "decided_by", length = 80)
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (submittedAt == null) submittedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
