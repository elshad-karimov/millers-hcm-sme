package az.millers.hcm.attendance.domain;

import java.time.LocalTime;
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
 * A shift definition — a named window like "Morning 08:00–16:00" or
 * "Night 22:00–06:00" (M110). Distinct from {@link WorkSchedule}, which is
 * a weekly template; a Shift is a single block of time that can be assigned
 * to an employee on a specific date via {@link RosterEntry}.
 */
@Entity
@Table(name = "shift", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class Shift {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    /**
     * True when {@link #endTime} is on the next calendar day (night shifts).
     * The service infers and writes this on save so consumers don't have to
     * re-derive the cross-midnight rule from the times.
     */
    @Column(name = "crosses_midnight", nullable = false)
    private boolean crossesMidnight;

    /** Hex like {@code #1677ff} for calendar/roster colour-coding. Nullable. */
    @Column(length = 7)
    private String color;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;

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
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    /**
     * Number of minutes between {@link #startTime} and {@link #endTime},
     * accounting for cross-midnight shifts, minus {@link #breakMinutes}.
     * Pure math — see {@link az.millers.hcm.attendance.service.ShiftService}
     * for the canonical implementation; this convenience wrapper exists for
     * one-shot JSON rendering.
     */
    public int durationMinutes() {
        int total = az.millers.hcm.attendance.service.ShiftService
                .spanMinutes(startTime, endTime, crossesMidnight);
        return Math.max(0, total - breakMinutes);
    }
}
