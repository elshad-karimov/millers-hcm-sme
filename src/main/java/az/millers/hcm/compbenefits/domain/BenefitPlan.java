package az.millers.hcm.compbenefits.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A benefit plan in the catalog (M108). Defines coverage, contribution split,
 * eligibility, and the effective-dated window during which the plan can be
 * offered to employees.
 */
@Entity
@Table(name = "benefit_plan", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitPlan {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false, length = 20)
    private BenefitType benefitType;

    @Column(length = 160)
    private String provider;

    @Column(name = "coverage_details", columnDefinition = "text")
    private String coverageDetails;

    @Column(columnDefinition = "text")
    private String eligibility;

    @Column(name = "employer_contribution", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerContribution = BigDecimal.ZERO;

    @Column(name = "employee_contribution", nullable = false, precision = 14, scale = 2)
    private BigDecimal employeeContribution = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency = "AZN";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    /** Total monthly cost (employer + employee). */
    public BigDecimal totalContribution() {
        return employerContribution.add(employeeContribution);
    }
}
