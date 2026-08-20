package az.millers.hcm.timesheet.domain;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * A request to change a day in a month that is already approved or locked.
 *
 * <p>The alternative — letting someone edit a closed month — destroys the
 * record of what was approved, which is the one thing an approval is for.
 * Approving this request reopens exactly the named day and nothing else.
 */
@Entity
@Table(name = "correction_request", schema = "timesheet")
@Getter
@Setter
@NoArgsConstructor
public class TimesheetCorrectionRequest {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "timesheet_id", nullable = false)
    private UUID timesheetId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** What the day says now, captured at request time. */
    @Column(name = "current_value", columnDefinition = "text")
    private String currentValue;

    @Column(name = "requested_value", nullable = false, columnDefinition = "text")
    private String requestedValue;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CorrectionStatus status = CorrectionStatus.PENDING;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (requestedAt == null) requestedAt = OffsetDateTime.now();
    }
}
