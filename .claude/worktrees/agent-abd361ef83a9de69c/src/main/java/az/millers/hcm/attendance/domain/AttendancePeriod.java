package az.millers.hcm.attendance.domain;

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
 * M330: Attendance period lock.
 *
 * <p>Controls when corrections are allowed. Once locked, period requires special
 * permission to unlock. Status: OPEN | LOCKED | CLOSED.
 */
@Entity
@Table(name = "attendance_period", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class AttendancePeriod {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(length = 20, nullable = false)
    private String status = "OPEN";

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "locked_by", length = 200)
    private String lockedBy;

    @Column(name = "unlocked_at")
    private OffsetDateTime unlockedAt;

    @Column(name = "unlocked_by", length = 200)
    private String unlockedBy;

    @Column(name = "employee_count_at_lock")
    private Integer employeeCountAtLock;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
