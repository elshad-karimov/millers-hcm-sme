package az.millers.hcm.timesheet.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * How much of one {@link TimeCategory} a single day carries.
 *
 * <p>Sparse: a day with twelve offshore hours is one row, not fourteen zeroes.
 */
@Entity
@Table(name = "day_quantity", schema = "timesheet",
        uniqueConstraints = @UniqueConstraint(columnNames = {"timesheet_day_id", "category_code"}))
@Getter
@Setter
@NoArgsConstructor
public class DayQuantity {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "timesheet_day_id", nullable = false)
    private UUID timesheetDayId;

    @Column(name = "category_code", nullable = false, length = 60)
    private String categoryCode;

    @Column(nullable = false, precision = 9, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    /**
     * Set when the system computed this value (HOLIDAY_CALENDAR, SHIFT_SCHEDULE,
     * LEAVE) — null means the employee typed it. Keeps a derived quantity
     * visibly distinct from a declared one during approval.
     */
    @Column(name = "derived_from", length = 20)
    private String derivedFrom;

    /** Why the employee overrode a derived value — demanded by validation. */
    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public DayQuantity(UUID timesheetDayId, String categoryCode, BigDecimal quantity) {
        this.timesheetDayId = timesheetDayId;
        this.categoryCode = categoryCode;
        this.quantity = quantity == null ? BigDecimal.ZERO : quantity;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isDerived() {
        return derivedFrom != null && !derivedFrom.isBlank();
    }
}
