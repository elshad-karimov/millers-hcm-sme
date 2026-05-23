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

    @Column(name = "review_no", nullable = false, unique = true)
    private String reviewNo;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

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

    @Column(name = "goal_score", precision = 6, scale = 3)
    private BigDecimal goalScore;

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
