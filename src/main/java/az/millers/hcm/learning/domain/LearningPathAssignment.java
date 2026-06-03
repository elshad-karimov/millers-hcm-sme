package az.millers.hcm.learning.domain;

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

/**
 * One employee's enrolment in a {@link LearningPath} template (M95).
 *
 * <p>Templates are abstract; assignments make them actionable. Per-step
 * progress is NOT stored here — it's derived at read time from the
 * existing {@link Enrollment} rows for the course-steps of this path,
 * so there's exactly one source of truth for "has this course been
 * passed".
 *
 * <p>Status transitions are gated in
 * {@code LearningPathAssignmentService}; the V69 CHECK constraints
 * ensure the schema matches.
 */
@Entity
@Table(name = "learning_path_assignment", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class LearningPathAssignment {

    @Id
    private UUID id;

    @Column(name = "path_id", nullable = false)
    private UUID pathId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "assigned_by", length = 80)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "target_completion_date")
    private LocalDate targetCompletionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PathAssignmentStatus status;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(columnDefinition = "text")
    private String notes;

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
        if (assignedAt == null) assignedAt = now;
        if (status == null) status = PathAssignmentStatus.ASSIGNED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
