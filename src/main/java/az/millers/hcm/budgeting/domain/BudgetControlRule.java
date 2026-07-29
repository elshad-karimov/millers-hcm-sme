package az.millers.hcm.budgeting.domain;

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
import lombok.Data;
import org.hibernate.annotations.TenantId;

/**
 * HCM_20 M428 — Budget control rule (PRD §20.6).
 * Defines WARN/BLOCK actions when a salary change / new hire / overtime
 * event exceeds budget threshold %.
 */
@Data
@Entity
@Table(name = "budget_control_rule", schema = "budgeting")
public class BudgetControlRule {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_point", nullable = false, length = 30)
    private TriggerPoint triggerPoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ControlAction action = ControlAction.WARN;

    @Column(name = "threshold_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal thresholdPct = BigDecimal.valueOf(100);

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
}
