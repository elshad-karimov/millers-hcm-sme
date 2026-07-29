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
import org.hibernate.annotations.TenantId;

/**
 * A single (employee, date) → {@link Shift} assignment (M110). The unique
 * constraint on {@code (employee_id, roster_date)} guarantees at most one
 * shift per employee per day — multi-shift coverage should be expressed as
 * a single Shift definition spanning the combined hours.
 *
 * <p>{@link #locked} freezes the row against further editing. Set after the
 * roster is "published" to employees so changes have to go through a swap
 * request rather than silent rewrite.
 */
@Entity
@Table(name = "roster_entry", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class RosterEntry {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "roster_date", nullable = false)
    private LocalDate rosterDate;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
