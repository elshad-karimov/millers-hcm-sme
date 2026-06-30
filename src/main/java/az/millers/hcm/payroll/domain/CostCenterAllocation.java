package az.millers.hcm.payroll.domain;

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

/**
 * M355 — Effective-dated employee cost center allocation.
 * Sum of allocations for an employee on any given date must = 100.00.
 */
@Entity
@Table(name = "cost_center_allocation", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class CostCenterAllocation {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "cost_center_code", nullable = false, length = 100)
    private String costCenterCode;

    @Column(name = "allocation_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal allocationPct;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
