package az.millers.hcm.timesheet.domain;

import java.math.BigDecimal;
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

    // ---- Daily capture (prd/timesheet-daily-capture) ----------------------
    // Where the day was worked. Decides which time categories may be entered
    // against it. Null on rows generated before the work-type dimension
    // existed — "never classified", which must stay distinct from ONSHORE.
    @Column(name = "work_type", length = 20)
    @Enumerated(EnumType.STRING)
    private WorkType workType;

    /** Who put the numbers there — employee declaration vs derived vs HR. */
    @Column(name = "entry_source", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EntrySource entrySource = EntrySource.ATTENDANCE;

    @Column(name = "employee_note", columnDefinition = "text")
    private String employeeNote;

    /** Entered hours minus attendance hours; null when there is no attendance. */
    @Column(name = "attendance_variance_hours", precision = 6, scale = 2)
    private BigDecimal attendanceVarianceHours;

    @Column(name = "variance_explanation", columnDefinition = "text")
    private String varianceExplanation;

    // ---- Approval (PRD/timesheet-approval-control) ------------------------
    /** Per-day state, so a return can name days instead of the whole month. */
    @Column(name = "approval_state", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DayApprovalState approvalState = DayApprovalState.PENDING;

    @Column(name = "return_reason", columnDefinition = "text")
    private String returnReason;

    @Column(name = "returned_by")
    private String returnedBy;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    // M484: Project dimension
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "task_code", length = 60)
    private String taskCode;

    @Column(name = "billable")
    private Boolean billable;

    /** V322: where the day was worked (SCV, BDWJF, Business Trip…). */
    @Column(name = "work_location", length = 80)
    private String workLocation;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
