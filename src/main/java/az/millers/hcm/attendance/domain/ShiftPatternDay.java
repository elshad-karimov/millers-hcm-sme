package az.millers.hcm.attendance.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * One day inside a {@link ShiftPattern} cycle (M111).
 *
 * <p>{@code shiftId} is nullable: a null value represents a <em>rest day</em>
 * in the rotation, which the auto-roster generator silently skips (no
 * RosterEntry row is produced for that date).
 */
@Entity
@Table(name = "shift_pattern_day", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class ShiftPatternDay {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "pattern_id", nullable = false)
    private UUID patternId;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    /** Nullable — null = OFF (rest day). */
    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(columnDefinition = "text")
    private String notes;

    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }
}
