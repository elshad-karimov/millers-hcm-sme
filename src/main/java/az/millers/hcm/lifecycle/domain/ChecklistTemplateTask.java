package az.millers.hcm.lifecycle.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One ordered task in a {@link ChecklistTemplate} (M105/M106). */
@Entity
@Table(name = "checklist_template_task", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ChecklistTemplateTask {

    @Id
    private UUID id;

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

    @Column(name = "due_offset_days")
    private Integer dueOffsetDays;

    @Column(nullable = false)
    private boolean required = true;

    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }
}
