package az.millers.hcm.payroll.timepay;

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
 * One employee priced differently from the catalog for one category.
 *
 * <p>The January 2026 workbook does not price everyone the same way — one
 * employee's offshore line is {@code salary x 1.75} regardless of hours. That
 * is either a real contractual basis or a mistake, and either way it belongs in
 * dated configuration with a stated reason rather than in the engine.
 *
 * <p>Effective-dated so changing someone's pay basis is a dated fact, not an
 * edit that rewrites what they were already paid.
 */
@Entity
@Table(name = "time_pay_rule_override", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class TimePayRuleOverride {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "category_code", nullable = false, length = 60)
    private String categoryCode;

    /** Null keeps the catalog's basis and overrides only the multiplier. */
    @Column(length = 30)
    private String basis;

    @Column(precision = 8, scale = 4)
    private BigDecimal multiplier;

    @Column(name = "flat_amount", precision = 12, scale = 2)
    private BigDecimal flatAmount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** Required: an exception to the pay rules must say why it exists. */
    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public boolean coversPeriodStart(LocalDate periodStart) {
        return !effectiveFrom.isAfter(periodStart)
                && (effectiveTo == null || !effectiveTo.isBefore(periodStart));
    }

    /** The catalog rule with this override applied — never mutates the catalog row. */
    public TimePayRule applyTo(TimePayRule base) {
        TimePayRule merged = new TimePayRule();
        merged.setCategoryCode(base.getCategoryCode());
        merged.setBasis(basis != null ? basis : base.getBasis());
        merged.setMultiplier(multiplier != null ? multiplier : base.getMultiplier());
        merged.setFlatAmount(flatAmount != null ? flatAmount : base.getFlatAmount());
        merged.setExemptPerUnit(base.getExemptPerUnit());
        merged.setDisplayOrder(base.getDisplayOrder());
        merged.setActive(base.isActive());
        merged.setNote(base.getNote() + " (overridden: " + reason + ")");
        return merged;
    }
}
