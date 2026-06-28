package az.millers.hcm.attendance.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M332: Attendance exception record.
 *
 * <p>Generated when a daily summary violates an enabled exception rule.
 * Status: OPEN | ACKNOWLEDGED | RESOLVED.
 */
@Entity
@Table(name = "attendance_exception", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceException {

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

    @Column(name = "exception_type", length = 50, nullable = false)
    private String exceptionType;

    @Column(length = 20, nullable = false)
    private String severity = "WARNING";

    @Column(name = "threshold_minutes", nullable = false)
    private int thresholdMinutes = 0;

    @Column(name = "actual_minutes", nullable = false)
    private int actualMinutes = 0;

    @Column(length = 20, nullable = false)
    private String status = "OPEN";

    @Column(name = "acknowledged_by", length = 200)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_by", length = 200)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
