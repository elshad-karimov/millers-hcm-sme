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
@Table(name = "commission_plan", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class CommissionPlan {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String basis;

    @Column(name = "flat_rate_pct", precision = 6, scale = 2)
    private BigDecimal flatRatePct;

    @Column
    private Boolean tiered = false;

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
