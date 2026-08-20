package az.millers.hcm.payroll.profile;

import java.math.BigDecimal;
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
import org.hibernate.annotations.TenantId;

/**
 * One rotation employee's excess balance for one balancing period.
 *
 * <p>Summarised working-time accounting: overtime is not decided monthly.
 * Months 1–3 of a period accumulate only and pay nothing; at the settlement
 * month the accumulated difference between actual and norm hours becomes
 * payable — and only if it is positive.
 *
 * <p>The running balance may go negative mid-period (that is the point of
 * summarised accounting). Only the settled figure is floored at zero: a
 * below-norm period pays nothing and does not create a debt.
 *
 * <p>Append-only. Settling writes the figures that were used and never clears
 * them, so a payment can always be read back exactly as it was decided
 * (global rules 11–12).
 */
@Entity
@Table(name = "excess_accumulator", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class ExcessAccumulator {

    public static final String OPEN = "OPEN";
    public static final String SETTLED = "SETTLED";

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "scheme_code", nullable = false, length = 40)
    private String schemeCode;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_seq", nullable = false)
    private int periodSeq;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "settlement_year", nullable = false)
    private int settlementYear;

    @Column(name = "settlement_month", nullable = false)
    private int settlementMonth;

    @Column(nullable = false, length = 16)
    private String status = OPEN;

    /** Running total of (actual − norm). May be negative mid-period. */
    @Column(name = "balance_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceHours = BigDecimal.ZERO;

    @Column(name = "settled_excess_hours", precision = 10, scale = 2)
    private BigDecimal settledExcessHours;

    @Column(name = "settled_multiplier", precision = 8, scale = 4)
    private BigDecimal settledMultiplier;

    @Column(name = "settled_amount", precision = 14, scale = 2)
    private BigDecimal settledAmount;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "settled_by")
    private String settledBy;

    /** Which payroll period carried the settlement. */
    @Column(name = "settled_in_period_year")
    private Integer settledInPeriodYear;

    @Column(name = "settled_in_period_month")
    private Integer settledInPeriodMonth;

    @Column(name = "settlement_note", columnDefinition = "text")
    private String settlementNote;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public boolean isSettled() {
        return SETTLED.equals(status);
    }

    /** Payable hours if the period closed now — never negative. */
    public BigDecimal payableHours() {
        return balanceHours.signum() > 0 ? balanceHours : BigDecimal.ZERO;
    }
}
