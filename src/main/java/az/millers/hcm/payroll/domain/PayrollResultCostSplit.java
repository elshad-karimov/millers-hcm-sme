package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
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
 * M355 — Snapshot of how each payroll result was allocated across cost centers.
 */
@Entity
@Table(name = "payroll_result_cost_split", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class PayrollResultCostSplit {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    @Column(name = "cost_center_code", nullable = false, length = 100)
    private String costCenterCode;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "allocation_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal allocationPct;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
