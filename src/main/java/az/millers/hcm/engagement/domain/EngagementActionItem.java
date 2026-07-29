package az.millers.hcm.engagement.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.TenantId;

/**
 * M479 — Engagement action plan item.
 */
@Entity
@Table(name = "engagement_action_item", schema = "engagement")
public class EngagementActionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "responsible_username")
    private String responsibleUsername;

    @Column(nullable = false)
    private Boolean done = false;

    @Column(name = "done_at")
    private OffsetDateTime doneAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getResponsibleUsername() { return responsibleUsername; }
    public void setResponsibleUsername(String responsibleUsername) { this.responsibleUsername = responsibleUsername; }

    public Boolean getDone() { return done; }
    public void setDone(Boolean done) { this.done = done; }

    public OffsetDateTime getDoneAt() { return doneAt; }
    public void setDoneAt(OffsetDateTime doneAt) { this.doneAt = doneAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
