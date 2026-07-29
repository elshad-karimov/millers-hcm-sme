package az.millers.hcm.timesheet.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "timesheet_day", schema = "timesheet",
        uniqueConstraints = @UniqueConstraint(columnNames = {"timesheet_id", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
public class TimesheetDay {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "timesheet_id", nullable = false)
    private UUID timesheetId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** W / L / S / BT / P / O / H / A (PRD 8.8.2). */
    @Column(name = "primary_code", nullable = false, length = 4)
    private String primaryCode;

    @Column(name = "worked_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal workedHours = BigDecimal.ZERO;

    @Column(name = "overtime_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "break_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal breakHours = BigDecimal.ZERO;

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes;

    @Column(name = "early_minutes", nullable = false)
    private int earlyMinutes;

    @Column(name = "source_summary_id")
    private UUID sourceSummaryId;

    @Column(name = "leave_request_id")
    private UUID leaveRequestId;

    @Column(name = "bt_request_id")
    private UUID btRequestId;

    @Column(name = "permission_request_id")
    private UUID permissionRequestId;

    @Column(columnDefinition = "text")
    private String anomalies;

    @Column(name = "correction_reason", columnDefinition = "text")
    private String correctionReason;

    @Column(name = "corrected_by")
    private String correctedBy;

    @Column(name = "corrected_at")
    private OffsetDateTime correctedAt;

    // M484: Project dimension
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "task_code", length = 60)
    private String taskCode;

    @Column(name = "billable")
    private Boolean billable;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
