package az.millers.hcm.compensation.domain;

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
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "commission_tier", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class CommissionTier {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "from_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", precision = 14, scale = 2)
    private BigDecimal toAmount;

    @Column(name = "rate_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal ratePct;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
