package az.millers.hcm.engagement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * M481: Employee points wallet. One per employee per tenant.
 */
@Entity
@Table(
    name = "reward_wallet",
    schema = "engagement",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "employee_id"})
)
public class RewardWallet {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private Integer balance = 0;

    @Column(name = "lifetime_earned", nullable = false)
    private Integer lifetimeEarned = 0;

    @Column(name = "lifetime_spent", nullable = false)
    private Integer lifetimeSpent = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }

    public Integer getLifetimeEarned() { return lifetimeEarned; }
    public void setLifetimeEarned(Integer lifetimeEarned) { this.lifetimeEarned = lifetimeEarned; }

    public Integer getLifetimeSpent() { return lifetimeSpent; }
    public void setLifetimeSpent(Integer lifetimeSpent) { this.lifetimeSpent = lifetimeSpent; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
