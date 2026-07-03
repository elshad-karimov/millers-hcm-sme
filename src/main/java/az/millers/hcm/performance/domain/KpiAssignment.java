package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HCM_12 M390 — a KPI assigned to an employee for a review cycle (PRD §7.1). Holds
 * the latest measurement (actual → achievement % → rating); history in kpi_result.
 */
@Entity
@Table(name = "kpi_assignment", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class KpiAssignment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "kpi_id", nullable = false)
    private UUID kpiId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "assigned_target", nullable = false, precision = 14, scale = 2)
    private BigDecimal assignedTarget;

    /** Weight among the employee's KPIs for the cycle (informational; KPI section weight comes from the template). */
    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent = BigDecimal.ZERO;

    @Column(name = "actual_value", precision = 14, scale = 2)
    private BigDecimal actualValue;

    @Column(name = "achievement_percent", precision = 7, scale = 2)
    private BigDecimal achievementPercent;

    /** Rating on the 1–5 scale derived by the KPI's scoring model (§7.4). */
    @Column(precision = 4, scale = 2)
    private BigDecimal rating;

    /** ASSIGNED | IN_PROGRESS | MEASURED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "ASSIGNED";

    @Column(name = "assigned_by", length = 80)
    private String assignedBy;

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
}
