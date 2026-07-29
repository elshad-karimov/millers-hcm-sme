package az.millers.hcm.lifecycle.domain;

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
import org.hibernate.annotations.TenantId;

/** One ordered task in a {@link ChecklistTemplate} (M105/M106). */
@Entity
@Table(name = "checklist_template_task", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ChecklistTemplateTask {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "default_owner_role", length = 40)
    private String defaultOwnerRole;

    /** What the task is (M299) — drives default owner + Phase B downstream action. */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40)
    private ChecklistTaskType taskType = ChecklistTaskType.MANUAL_TASK;

    /** Onboarding stream/persona grouping (M299) — optional. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40)
    private ChecklistOnboardingCategory category;

    /** For TRAINING_ASSIGNMENT tasks (M303): COURSE or PATH the hire is auto-enrolled in. */
    @Enumerated(EnumType.STRING)
    @Column(name = "training_target_kind", length = 8)
    private TrainingTargetKind trainingTargetKind;

    /** LMS course id (kind COURSE) or learning-path id (kind PATH). */
    @Column(name = "training_target_id")
    private UUID trainingTargetId;

    @Column(name = "due_offset_days")
    private Integer dueOffsetDays;

    @Column(nullable = false)
    private boolean required = true;

    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }
}
