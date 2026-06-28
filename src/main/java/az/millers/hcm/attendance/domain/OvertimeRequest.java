package az.millers.hcm.attendance.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M329: Overtime request with workflow approval.
 *
 * <p>Supports both pre-approval (before work) and post-recording (after the fact).
 * Workflow: manager always, department head conditional when > 120 minutes.
 */
@Entity
@Table(name = "overtime_request", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class OvertimeRequest {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "summary_id")
    private UUID summaryId;

    @Column(name = "ot_start", nullable = false)
    private OffsetDateTime otStart;

    @Column(name = "ot_end", nullable = false)
    private OffsetDateTime otEnd;

    @Column(name = "requested_minutes", nullable = false)
    private int requestedMinutes;

    @Column(columnDefinition = "text", nullable = false)
    private String reason;

    @Column(name = "pre_approved", nullable = false)
    private boolean preApproved = false;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "workflow_status", length = 30, nullable = false)
    private String workflowStatus = "DRAFT";

    @Column(length = 30)
    private String decision;

    @Column(name = "decision_comment", columnDefinition = "text")
    private String decisionComment;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decided_by", length = 200)
    private String decidedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
