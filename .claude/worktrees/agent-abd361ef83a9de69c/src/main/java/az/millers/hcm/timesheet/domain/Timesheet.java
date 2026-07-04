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

@Entity
@Table(name = "timesheet", schema = "timesheet",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "period_year", "period_month"}))
@Getter
@Setter
@NoArgsConstructor
public class Timesheet {

    @Id
    private UUID id;

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

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

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
