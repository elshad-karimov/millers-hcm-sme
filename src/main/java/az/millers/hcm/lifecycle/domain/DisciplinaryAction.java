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
import org.hibernate.annotations.TenantId;

/**
 * One disciplinary record (M67 / P1-12). Goes through an approval workflow
 * before being issued to the employee.
 *
 * <p>Routes through the existing {@code WorkflowService.start()} infrastructure
 * (same pattern as {@link ContractChange} and {@link TerminationRequest}) —
 * the workflow_instance_id is populated when {@code DisciplinaryActionService.submit()}
 * kicks off the workflow, and the {@code LifecycleWorkflowListener} reacts to
 * the terminal {@code WorkflowCompletedEvent}.
 */
@Entity
@Table(name = "disciplinary_action", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class DisciplinaryAction {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "action_no", nullable = false, unique = true, length = 20)
    private String actionNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private DisciplinaryActionType actionType;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "action_date", nullable = false)
    private LocalDate actionDate;

    @Column(name = "issued_by", length = 80)
    private String issuedBy;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisciplinaryStatus status = DisciplinaryStatus.DRAFT;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    /** Soft FK to an antecedent action for progressive-discipline threading. */
    @Column(name = "linked_case_id")
    private UUID linkedCaseId;

    @Column(name = "appeal_flag", nullable = false)
    private boolean appealFlag = false;

    @Column(name = "appeal_reason", columnDefinition = "text")
    private String appealReason;

    @Column(name = "appeal_outcome", columnDefinition = "text")
    private String appealOutcome;

    @Column(name = "appealed_at")
    private OffsetDateTime appealedAt;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
