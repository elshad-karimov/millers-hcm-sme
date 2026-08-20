package az.millers.hcm.timesheet.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * The month's total for one {@link TimeCategory} — recomputed from the days on
 * every change.
 *
 * <p><strong>This is the payroll input contract.</strong> When payroll starts
 * pricing these quantities it reads this table and nothing else, so the seam
 * between "what happened" and "what it is worth" stays one narrow, auditable
 * surface rather than payroll reaching into day rows.
 */
@Entity
@Table(name = "timesheet_month_total", schema = "timesheet",
        uniqueConstraints = @UniqueConstraint(columnNames = {"timesheet_id", "category_code"}))
@Getter
@Setter
@NoArgsConstructor
public class TimesheetMonthTotal {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "timesheet_id", nullable = false)
    private UUID timesheetId;

    @Column(name = "category_code", nullable = false, length = 60)
    private String categoryCode;

    @Column(nullable = false, precision = 11, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    public TimesheetMonthTotal(UUID timesheetId, String categoryCode, BigDecimal quantity) {
        this.timesheetId = timesheetId;
        this.categoryCode = categoryCode;
        this.quantity = quantity == null ? BigDecimal.ZERO : quantity;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (computedAt == null) computedAt = OffsetDateTime.now();
    }
}
