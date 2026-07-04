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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-employee instantiation of a checklist template (M105/M106). */
@Entity
@Table(name = "checklist_assignment", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ChecklistAssignment {

    @Id
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 20)
    private ChecklistFlowType flowType;

    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChecklistAssignmentStatus status = ChecklistAssignmentStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "started_by", length = 80)
    private String startedBy;

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
        if (startedAt == null) startedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
