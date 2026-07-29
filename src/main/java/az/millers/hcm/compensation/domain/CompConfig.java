package az.millers.hcm.compensation.domain;

import java.math.BigDecimal;
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
import org.hibernate.annotations.TenantId;

/**
 * M359 — Tenant-scoped compensation configuration.
 * One row per tenant; seed defaults for 'default'.
 */
@Entity
@Table(name = "comp_config", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class CompConfig {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "max_increase_pct_without_approval", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxIncreasePctWithoutApproval = new BigDecimal("15.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_exceeded_policy", nullable = false, length = 30)
    private BudgetExceededPolicy budgetExceededPolicy = BudgetExceededPolicy.WARNING;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "AZN";

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
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
