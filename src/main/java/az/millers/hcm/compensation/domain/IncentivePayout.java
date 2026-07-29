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
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "incentive_payout", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class IncentivePayout {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false, length = 20)
    private String period;

    @Column(name = "eligible_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal eligibleSalary;

    @Column(name = "achievement_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal achievementPct;

    @Column(name = "payout_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal payoutPct;

    @Column(name = "payout_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal payoutAmount;

    @Column(length = 20)
    private String status;

    @Column(name = "payroll_bonus_id")
    private UUID payrollBonusId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
