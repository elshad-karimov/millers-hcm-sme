package az.millers.hcm.compensation.domain;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M364 — Compensation budget: tracks allocated vs consumed budget for merit/bonus/etc.
 * by scope (global, department, manager, grade, legal entity).
 */
@Entity
@Table(name = "compensation_budget", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class CompensationBudget {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "cycle_id")
    private UUID cycleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private BudgetScopeType scopeType;

    @Column(name = "scope_ref", length = 100)
    private String scopeRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_type", nullable = false, length = 20)
    private BudgetType budgetType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "consumed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal consumedAmount = BigDecimal.ZERO;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

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
