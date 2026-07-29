package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import org.hibernate.annotations.TenantId;

/**
 * M349 — Configurable salary component catalog (EARNING/DEDUCTION).
 * Drives payroll calculation via per-employee assignments.
 */
@Entity
@Table(name = "salary_component", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class SalaryComponent {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComponentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_method", nullable = false, length = 30)
    private CalculationMethod calculationMethod;

    @Column(name = "default_amount", precision = 12, scale = 2)
    private BigDecimal defaultAmount;

    /** For PERCENTAGE_OF_BASE: 0.1000 = 10% of base salary. */
    @Column(precision = 5, scale = 4)
    private BigDecimal percentage;

    /** TRUE = included in income tax base. */
    @Column(name = "is_taxable", nullable = false)
    private Boolean isTaxable = true;

    /** TRUE = exempt from DSMF/MMI base (only for EARNING kind). */
    @Column(name = "contribution_exempt", nullable = false)
    private Boolean contributionExempt = false;

    /** TRUE = statutory component (cannot be edited/deleted). */
    @Column(name = "is_statutory", nullable = false)
    private Boolean isStatutory = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
