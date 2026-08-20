package az.millers.hcm.payroll.timepay;

import java.math.BigDecimal;
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
import org.hibernate.annotations.TenantId;

/**
 * What one hour or day of a timesheet category is worth.
 *
 * <p>Every multiplier is transcribed from a formula in the January 2026
 * workbook, not chosen. They are <strong>absolute, not premiums</strong>:
 * offshore at 1.75 pays 1.75x the hourly rate in total, not the rate plus 75%.
 */
@Entity
@Table(name = "time_pay_rule", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class TimePayRule {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    /** Matches {@code timesheet.time_category.code}. */
    @Column(name = "category_code", nullable = false, length = 60)
    private String categoryCode;

    /** HOURLY_RATE, OVERTIME_RATE or FLAT_PER_UNIT. */
    @Column(nullable = false, length = 20)
    private String basis;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal multiplier = BigDecimal.ONE;

    @Column(name = "flat_amount", precision = 12, scale = 2)
    private BigDecimal flatAmount;

    /**
     * Paid in full, but this much per unit is removed from every contribution
     * base — the workbook pays meal at 12 AZN/day and subtracts days x 5.
     */
    @Column(name = "exempt_per_unit", nullable = false, precision = 12, scale = 2)
    private BigDecimal exemptPerUnit = BigDecimal.ZERO;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 100;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
