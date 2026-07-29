package az.millers.hcm.attendance.domain;

import java.time.LocalDate;
import java.time.LocalTime;
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
@Table(name = "daily_summary", schema = "attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
public class DailySummary {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    /** M112 — Populated when {@link #source} = ROSTER. */
    @Column(name = "shift_id")
    private UUID shiftId;

    /** M112 — Where the scheduled window came from: SCHEDULE / ROSTER / NONE. */
    @Column(name = "source", nullable = false, length = 20)
    private String source = "SCHEDULE";

    @Column(name = "schedule_start")
    private LocalTime scheduleStart;

    @Column(name = "schedule_end")
    private LocalTime scheduleEnd;

    @Column(name = "entry_time")
    private OffsetDateTime entryTime;

    @Column(name = "exit_time")
    private OffsetDateTime exitTime;

    @Column(name = "raw_event_count", nullable = false)
    private int rawEventCount;

    @Column(name = "worked_minutes", nullable = false)
    private int workedMinutes;

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes;

    @Column(name = "early_minutes", nullable = false)
    private int earlyMinutes;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SummaryStatus status;

    /** M326 — which attendance policy was applied when this summary was computed. */
    @Column(name = "policy_id")
    private UUID policyId;

    /** M326 — COMPUTED | APPROVED | PAYROLL_PROCESSED. */
    @Column(name = "calculation_status", nullable = false, length = 30)
    private String calculationStatus = "COMPUTED";

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by", length = 200)
    private String approvedBy;

    @Column(name = "correction_reason", columnDefinition = "text")
    private String correctionReason;

    @Column(name = "corrected_by")
    private String correctedBy;

    @Column(name = "corrected_at")
    private OffsetDateTime correctedAt;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (computedAt == null) computedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        if (computedAt == null) computedAt = OffsetDateTime.now();
    }
}
