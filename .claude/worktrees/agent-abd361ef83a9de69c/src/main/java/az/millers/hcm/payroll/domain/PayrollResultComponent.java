package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M349 — Per-payslip component snapshot (audit trail for each payroll line item).
 */
@Entity
@Table(name = "payroll_result_component", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class PayrollResultComponent {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    @Column(name = "component_id")
    private UUID componentId;

    @Column(name = "component_code", nullable = false, length = 50)
    private String componentCode;

    @Column(name = "component_name", nullable = false, length = 200)
    private String componentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComponentKind kind;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_taxable", nullable = false)
    private Boolean isTaxable;

    @Column(name = "contribution_exempt", nullable = false)
    private Boolean contributionExempt = false;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
