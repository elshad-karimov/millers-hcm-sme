package az.millers.hcm.engagement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * M481: Reward catalog item. Employees redeem points for catalog items.
 */
@Entity
@Table(
    name = "reward_catalog",
    schema = "engagement",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "code"})
)
public class RewardCatalog {

    @Id
    @GeneratedValue
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "monetary_value", precision = 14, scale = 2)
    private BigDecimal monetaryValue;

    @Column(length = 60)
    private String category;

    @Column(nullable = false)
    private Boolean taxable = false;

    @Column(nullable = false)
    private Boolean active = true;

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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public BigDecimal getMonetaryValue() { return monetaryValue; }
    public void setMonetaryValue(BigDecimal monetaryValue) { this.monetaryValue = monetaryValue; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Boolean getTaxable() { return taxable; }
    public void setTaxable(Boolean taxable) { this.taxable = taxable; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
