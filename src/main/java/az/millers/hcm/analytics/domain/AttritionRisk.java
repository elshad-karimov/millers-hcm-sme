package az.millers.hcm.analytics.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;

/**
 * M476 — Attrition risk heuristic scoring.
 * HR-only, never shown to employees/managers.
 */
@Entity
@Table(name = "attrition_risk", schema = "analytics",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "employee_id"}))
public class AttritionRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private Integer score;

    @Column(length = 500)
    private String factors;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt = OffsetDateTime.now();

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getFactors() { return factors; }
    public void setFactors(String factors) { this.factors = factors; }

    public OffsetDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(OffsetDateTime computedAt) { this.computedAt = computedAt; }
}
