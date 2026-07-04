package az.millers.hcm.payroll.domain;

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

/**
 * Snapshot of an allowance line attached to one payroll run. Mirrors
 * {@link PayrollBonus} but carries a {@code taxable} flag so the engine
 * knows whether to push the amount through gross (taxed +
 * DSMF/MMI/unempl) or straight into net (post-stat).
 *
 * <p>Written by {@code PayrollEngine.calculate()} at run time — recalc
 * deletes-and-rebuilds these rows. Closed/paid runs keep their snapshot
 * even if the underlying {@code comp_benefits.employee_allowance} or
 * {@code allowance_type} catalogue changes later.
 */
@Entity
@Table(name = "payroll_allowance", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class PayrollAllowance {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** Soft ref into {@code comp_benefits.employee_allowance.id} — no FK. */
    @Column(name = "employee_allowance_id")
    private UUID employeeAllowanceId;

    /** Soft ref into {@code comp_benefits.allowance_type.id} — no FK. */
    @Column(name = "allowance_type_id")
    private UUID allowanceTypeId;

    @Column(name = "allowance_type_code", length = 40)
    private String allowanceTypeCode;

    @Column(name = "allowance_type_name", length = 160)
    private String allowanceTypeName;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Column(nullable = false)
    private boolean taxable = true;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
