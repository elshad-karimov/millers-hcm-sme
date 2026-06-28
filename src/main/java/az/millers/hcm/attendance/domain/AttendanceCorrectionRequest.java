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
 * M328: Attendance correction request with workflow approval.
 *
 * <p>Allows employees and HR to correct clock-in/out times or change summary status.
 * Workflow approval required (manager always, HR conditional based on period lock,
 * absence status change, or overtime delta).
 */
@Entity
@Table(name = "attendance_correction_request", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceCorrectionRequest {

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

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "requested_clock_in")
    private OffsetDateTime requestedClockIn;

    @Column(name = "requested_clock_out")
    private OffsetDateTime requestedClockOut;

    @Column(name = "requested_status", length = 30)
    private String requestedStatus;

    @Column(columnDefinition = "text", nullable = false)
    private String reason;

    @Column(name = "correction_type", length = 30, nullable = false)
    private String correctionType = "CLOCK_TIME";

    @Column(name = "absence_status_changed", nullable = false)
    private boolean absenceStatusChanged = false;

    @Column(name = "overtime_delta_minutes", nullable = false)
    private int overtimeDeltaMinutes = 0;

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
