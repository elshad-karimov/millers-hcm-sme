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
 * How one employee's excess / MEWA amount is worked out.
 *
 * <p>Per employee because the workbook has no single rule: four employees carry
 * four different formulas, and the declared "Excess hours" column is zero in
 * every row, so the quantity does not drive the amount at all. Guessing here
 * would silently change someone's pay, so an employee without a rule earns no
 * excess and the calculation says so.
 */
@Entity
@Table(name = "employee_excess_rule", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeExcessRule {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** PERCENT_OF_ONSHORE or UNITS_AT_RATE. */
    @Column(nullable = false, length = 20)
    private String method;

    /** For PERCENT_OF_ONSHORE — e.g. 0.3000 for the workbook's 30%. */
    @Column(precision = 6, scale = 4)
    private BigDecimal percentage;

    /** For UNITS_AT_RATE — the hardcoded unit count the workbook uses. */
    @Column(precision = 9, scale = 2)
    private BigDecimal units;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal multiplier = new BigDecimal("1.6");

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(columnDefinition = "text")
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
}
