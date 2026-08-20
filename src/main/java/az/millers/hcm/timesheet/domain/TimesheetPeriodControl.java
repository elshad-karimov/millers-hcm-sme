package az.millers.hcm.timesheet.domain;

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

/**
 * Whether a monthly timesheet period is still open.
 *
 * <p>Locking is HR's act and the gate payroll waits behind. It is refused while
 * any timesheet in the period is still SUBMITTED or RETURNED — locking an
 * unapproved month is exactly how unapproved hours reach payroll.
 */
@Entity
@Table(name = "period_control", schema = "timesheet",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "period_year", "period_month"}))
@Getter
@Setter
@NoArgsConstructor
public class TimesheetPeriodControl {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PeriodStatus status = PeriodStatus.OPEN;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "lock_reason", columnDefinition = "text")
    private String lockReason;

    @Column(name = "unlocked_at")
    private OffsetDateTime unlockedAt;

    @Column(name = "unlocked_by")
    private String unlockedBy;

    @Column(name = "unlock_reason", columnDefinition = "text")
    private String unlockReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public TimesheetPeriodControl(int periodYear, int periodMonth) {
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
    }

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

    public boolean isLocked() {
        return status == PeriodStatus.LOCKED;
    }
}
