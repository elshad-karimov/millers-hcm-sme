package az.millers.hcm.staffing.domain;

import java.math.BigDecimal;
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
 * Versioned monthly budget for a position (M244 / PRD §6).
 *
 * <p>One row per (position, effective_from). The active budget for a
 * given as-of date is the row whose {@code effective_from <= asOf} and
 * {@code (effective_to IS NULL OR effective_to >= asOf)}. Older rows
 * are kept for variance reporting / historical headcount cost analysis.
 *
 * <p>Total monthly cost is the sum of the six component columns; we
 * compute it in {@link #totalMonthly()} rather than storing it so a
 * single transaction can edit any component without re-deriving.
 */
@Entity
@Table(name = "position_budget", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class PositionBudget {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "budgeted_basic_salary", precision = 14, scale = 2, nullable = false)
    private BigDecimal budgetedBasicSalary = BigDecimal.ZERO;

    @Column(name = "budgeted_allowances", precision = 14, scale = 2, nullable = false)
    private BigDecimal budgetedAllowances = BigDecimal.ZERO;

    @Column(name = "budgeted_employer_tax", precision = 14, scale = 2, nullable = false)
    private BigDecimal budgetedEmployerTax = BigDecimal.ZERO;

    @Column(name = "budgeted_bonus", precision = 14, scale = 2, nullable = false)
    private BigDecimal budgetedBonus = BigDecimal.ZERO;

    @Column(name = "budgeted_overtime", precision = 14, scale = 2, nullable = false)
    private BigDecimal budgetedOvertime = BigDecimal.ZERO;

    @Column(name = "budgeted_benefits", precision = 14, scale = 2, nullable = false)
    private BigDecimal budgetedBenefits = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "budget_owner")
    private String budgetOwner;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    /** Sum of the six component columns. Annualised = × 12. */
    public BigDecimal totalMonthly() {
        return nz(budgetedBasicSalary)
                .add(nz(budgetedAllowances))
                .add(nz(budgetedEmployerTax))
                .add(nz(budgetedBonus))
                .add(nz(budgetedOvertime))
                .add(nz(budgetedBenefits));
    }

    public BigDecimal totalAnnual() {
        return totalMonthly().multiply(BigDecimal.valueOf(12));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (currency == null) currency = "AZN";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
