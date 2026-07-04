package az.millers.hcm.lifecycle.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-task progress on a {@link ChecklistAssignment} (M105/M106). */
@Entity
@Table(name = "checklist_task_status", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ChecklistTaskStatus {

    @Id
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "template_task_id", nullable = false)
    private UUID templateTaskId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "owner_role", length = 40)
    private String ownerRole;

    /** Snapshot of the template task's type (M299) — drives persona dashboards. */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40)
    private ChecklistTaskType taskType = ChecklistTaskType.MANUAL_TASK;

    /** Snapshot of the template task's onboarding category (M299) — optional. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40)
    private ChecklistOnboardingCategory category;

    /** Snapshot of the training target (M303) — drives auto-enrol on start + close-on-pass. */
    @Enumerated(EnumType.STRING)
    @Column(name = "training_target_kind", length = 8)
    private TrainingTargetKind trainingTargetKind;

    @Column(name = "training_target_id")
    private UUID trainingTargetId;

    @Column(name = "assigned_to_username", length = 80)
    private String assignedToUsername;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean required = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChecklistTaskStatusValue status = ChecklistTaskStatusValue.PENDING;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by", length = 80)
    private String completedBy;

    @Column(columnDefinition = "text")
    private String notes;

    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }
}
