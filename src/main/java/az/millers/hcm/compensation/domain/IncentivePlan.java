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
@Table(name = "incentive_plan", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class IncentivePlan {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String measure;

    @Column(name = "target_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetPct;

    @Column(name = "threshold_achievement", precision = 6, scale = 2)
    private BigDecimal thresholdAchievement;

    @Column(name = "target_achievement", precision = 6, scale = 2)
    private BigDecimal targetAchievement;

    @Column(name = "cap_achievement", precision = 6, scale = 2)
    private BigDecimal capAchievement;

    @Column(name = "max_payout_pct", precision = 6, scale = 2)
    private BigDecimal maxPayoutPct;

    @Column(length = 3)
    private String currency;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
