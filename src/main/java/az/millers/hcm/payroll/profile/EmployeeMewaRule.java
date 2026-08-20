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
 * One employee's MEWA entitlement.
 *
 * <p>MEWA is not excess. Slice 3's {@code employee_excess_rule} conflated them
 * because the January workbook shows both in one column; they are different
 * earnings with different drivers. Rates are observed at 30% and 60% of the
 * onshore amount and vary per employee — there is no global rule and none is
 * invented here (BLOCKERS Q7).
 */
@Entity
@Table(name = "employee_mewa_rule", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeMewaRule {

    public static final String BASIS_ONSHORE_EARNING = "ONSHORE_EARNING";

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false, length = 30)
    private String basis = BASIS_ONSHORE_EARNING;

    /** 0.3000 for the workbook's 30%. */
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

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
