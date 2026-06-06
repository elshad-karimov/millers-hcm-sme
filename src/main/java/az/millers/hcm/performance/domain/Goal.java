package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "goal", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class Goal {

    @Id
    private UUID id;

    @Column(name = "goal_no", nullable = false, unique = true)
    private String goalNo;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "parent_goal_id")
    private UUID parentGoalId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GoalCategory category;

    @Column(name = "target_metric", length = 240)
    private String targetMetric;

    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent = BigDecimal.ZERO;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GoalStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(precision = 4, scale = 2)
    private BigDecimal rating;

    @Column(name = "rating_note", columnDefinition = "text")
    private String ratingNote;

    /**
     * Optional FK to the LMS course that should auto-rate this goal when
     * the employee's enrollment is PASSED (M49 — PRD 8.13/8.14).
     * {@code null} = manual rating only.
     */
    @Column(name = "source_course_id")
    private UUID sourceCourseId;

    /**
     * M130 — when this goal was created by the cascade action, the
     * username + timestamp that did it. Plain create() leaves these
     * null. Lets the SPA show "Cascaded by X on Y" without hitting the
     * audit log.
     */
    @Column(name = "cascaded_by")
    private String cascadedBy;

    @Column(name = "cascaded_at")
    private OffsetDateTime cascadedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = GoalStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
