package az.millers.hcm.performance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A structured calibration meeting that groups all {@link PerformanceReview}
 * records for a cycle and tracks the facilitated session lifecycle
 * (PRD §8.13 – M56).
 *
 * <p>Lifecycle: {@code SCHEDULED → IN_PROGRESS → COMPLETED}
 */
@Entity
@Table(schema = "performance", name = "calibration_session")
@Getter
@Setter
@NoArgsConstructor
public class CalibrationSession {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    /** SCHEDULED | IN_PROGRESS | COMPLETED */
    @Column(nullable = false, length = 24)
    private String status;

    @Column(length = 120)
    private String facilitator;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    /** M121 — wall-clock when the session moved to COMPLETED and reviews were locked. */
    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    /** M121 — count of reviews sealed by this session's completion. Cached for the list-screen badge. */
    @Column(name = "locked_reviews", nullable = false)
    private int lockedReviews = 0;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = "SCHEDULED";
    }
}
