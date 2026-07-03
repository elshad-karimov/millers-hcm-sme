package az.millers.hcm.compbenefits.domain;

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

/**
 * HCM_11 M375 — a coverage tier of a benefit plan, with its own employer/employee
 * contribution split and coverage amount. A plan with no tiers falls back to its flat
 * plan-level contribution (M108 back-compat).
 */
@Entity
@Table(name = "benefit_plan_tier", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitPlanTier {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier_code", nullable = false, length = 30)
    private BenefitCoverageTier tierCode;

    @Column(name = "tier_label", length = 120)
    private String tierLabel;

    @Column(name = "employer_contribution", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerContribution = BigDecimal.ZERO;

    @Column(name = "employee_contribution", nullable = false, precision = 14, scale = 2)
    private BigDecimal employeeContribution = BigDecimal.ZERO;

    @Column(name = "coverage_amount", precision = 14, scale = 2)
    private BigDecimal coverageAmount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
