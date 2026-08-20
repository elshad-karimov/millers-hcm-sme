package az.millers.hcm.timesheet.domain;

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
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "timesheet", schema = "timesheet",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "period_year", "period_month"}))
@Getter
@Setter
@NoArgsConstructor
public class Timesheet {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimesheetStatus status;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "total_worked_hours", nullable = false, precision = 9, scale = 2)
    private BigDecimal totalWorkedHours = BigDecimal.ZERO;

    @Column(name = "total_overtime_hours", nullable = false, precision = 9, scale = 2)
    private BigDecimal totalOvertimeHours = BigDecimal.ZERO;

    @Column(name = "total_leave_days", nullable = false, precision = 9, scale = 2)
    private BigDecimal totalLeaveDays = BigDecimal.ZERO;

    @Column(name = "total_sick_days", nullable = false, precision = 9, scale = 2)
    private BigDecimal totalSickDays = BigDecimal.ZERO;

    @Column(name = "total_bt_days", nullable = false, precision = 9, scale = 2)
    private BigDecimal totalBtDays = BigDecimal.ZERO;

    @Column(name = "total_permission_hrs", nullable = false, precision = 9, scale = 2)
    private BigDecimal totalPermissionHrs = BigDecimal.ZERO;

    @Column(name = "total_absent_days", nullable = false, precision = 9, scale = 2)
    private BigDecimal totalAbsentDays = BigDecimal.ZERO;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "generated_by")
    private String generatedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    /** V324 stage 1 — the employee's direct manager. Final HR sign-off is {@link #approvedBy}. */
    @Column(name = "manager_approved_at")
    private OffsetDateTime managerApprovedAt;

    @Column(name = "manager_approved_by", length = 80)
    private String managerApprovedBy;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    // ---- Daily capture (prd/timesheet-daily-capture) ----------------------
    /** When the employee themselves confirmed the month is accurate. */
    @Column(name = "employee_confirmed_at")
    private OffsetDateTime employeeConfirmedAt;

    @Column(name = "employee_comment", columnDefinition = "text")
    private String employeeComment;

    /** Non-blocking findings carried to the approver, one per line. */
    @Column(name = "validation_warnings", columnDefinition = "text")
    private String validationWarnings;

    // ---- Approval trail (PRD/timesheet-approval-control) -------------------
    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "returned_by")
    private String returnedBy;

    @Column(name = "return_reason", columnDefinition = "text")
    private String returnReason;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

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
        if (generatedAt == null) generatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
