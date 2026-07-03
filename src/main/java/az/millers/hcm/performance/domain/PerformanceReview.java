package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "performance_review", schema = "performance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cycle_id", "employee_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PerformanceReview {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "review_no", nullable = false, unique = true)
    private String reviewNo;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    /** HCM_12 M389 — template this review form was generated from (frozen at launch). */
    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "manager_id")
    private UUID managerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewStatus status;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "self_rating", precision = 4, scale = 2)
    private BigDecimal selfRating;

    @Column(name = "self_comments", columnDefinition = "text")
    private String selfComments;

    @Column(name = "self_submitted_at")
    private OffsetDateTime selfSubmittedAt;

    @Column(name = "manager_rating", precision = 4, scale = 2)
    private BigDecimal managerRating;

    @Column(name = "manager_comments", columnDefinition = "text")
    private String managerComments;

    @Column(name = "manager_submitted_at")
    private OffsetDateTime managerSubmittedAt;

    @Column(name = "final_rating", precision = 4, scale = 2)
    private BigDecimal finalRating;

    @Column(name = "final_band", length = 40)
    private String finalBand;

    @Column(name = "calibration_notes", columnDefinition = "text")
    private String calibrationNotes;

    /** M92 — Potential rating 1-5 set during calibration. NULL until calibration adds it. */
    @Column(name = "potential_rating", precision = 4, scale = 2)
    private BigDecimal potentialRating;

    /** M92 — Rationale for the potential score (visible to HR + the employee's manager). */
    @Column(name = "potential_notes", columnDefinition = "text")
    private String potentialNotes;

    @Column(name = "goal_score", precision = 6, scale = 3)
    private BigDecimal goalScore;

    // ── M394 — §18 weighted section scoring + override ─────────────────────

    /** Weighted average of MEASURED KPI-assignment ratings (§18.2). */
    @Column(name = "kpi_score", precision = 6, scale = 3)
    private BigDecimal kpiScore;

    /** Average final competency level from the M393 assessment (§18.2). */
    @Column(name = "competency_score", precision = 6, scale = 3)
    private BigDecimal competencyScore;

    /** Company-values rating, entered by the manager (§18.2). */
    @Column(name = "values_score", precision = 6, scale = 3)
    private BigDecimal valuesScore;

    /** §18.2 — Σ(section score × template weight) / Σ(participating weights). */
    @Column(name = "overall_score", precision = 6, scale = 3)
    private BigDecimal overallScore;

    /** §18.3 — rating-scale band label for the overall score. */
    @Column(name = "overall_band", length = 120)
    private String overallBand;

    /** §18.4 — the pre-override computed rating, preserved once set. */
    @Column(name = "original_rating", precision = 6, scale = 3)
    private BigDecimal originalRating;

    @Column(name = "override_reason", length = 1000)
    private String overrideReason;

    @Column(name = "overridden_by", length = 80)
    private String overriddenBy;

    @Column(name = "overridden_at")
    private OffsetDateTime overriddenAt;

    @Column(length = 40)
    private String recommendation;

    @Column(name = "bonus_percent", precision = 5, scale = 2)
    private BigDecimal bonusPercent;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    /**
     * M121 — once a {@link CalibrationSession} that touched this review
     * is COMPLETED, this flag flips to {@code true} and the calibrate /
     * manager-review write paths reject further edits without an HR
     * re-open. Prevents a stale UI tab from silently overwriting a
     * calibration outcome.
     */
    @Column(name = "calibration_locked", nullable = false)
    private boolean calibrationLocked = false;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = ReviewStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
