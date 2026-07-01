package az.millers.hcm.compensation.domain;

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

@Entity
@Table(name = "total_comp_statement", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class TotalCompStatement {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private int year;

    @Column(name = "base_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseSalary = BigDecimal.ZERO;

    @Column(name = "allowances_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal allowancesTotal = BigDecimal.ZERO;

    @Column(name = "bonus_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal bonusTotal = BigDecimal.ZERO;

    @Column(name = "incentives_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal incentivesTotal = BigDecimal.ZERO;

    @Column(name = "commission_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal commissionTotal = BigDecimal.ZERO;

    @Column(name = "employer_benefits_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerBenefitsTotal = BigDecimal.ZERO;

    @Column(name = "employer_contributions_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerContributionsTotal = BigDecimal.ZERO;

    @Column(name = "total_comp", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalComp = BigDecimal.ZERO;

    @Column(length = 3)
    private String currency;

    @Column(length = 20)
    private String status;

    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (currency == null) currency = "AZN";
        if (status == null) status = "GENERATED";
        if (generatedAt == null) generatedAt = OffsetDateTime.now();
    }
}
