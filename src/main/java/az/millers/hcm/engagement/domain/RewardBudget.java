package az.millers.hcm.engagement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * M481: Annual reward budget per org unit (or global if org_unit_id is null).
 */
@Entity
@Table(
    name = "reward_budget",
    schema = "engagement",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "year", "org_unit_id"})
)
public class RewardBudget {

    @Id
    @GeneratedValue
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "used_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public UUID getOrgUnitId() { return orgUnitId; }
    public void setOrgUnitId(UUID orgUnitId) { this.orgUnitId = orgUnitId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getUsedAmount() { return usedAmount; }
    public void setUsedAmount(BigDecimal usedAmount) { this.usedAmount = usedAmount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
