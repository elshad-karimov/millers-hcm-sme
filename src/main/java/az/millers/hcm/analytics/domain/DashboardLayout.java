package az.millers.hcm.analytics.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.TenantId;

/**
 * M474 — Saved dashboard layout with KPI widgets.
 */
@Entity
@Table(name = "dashboard_layout", schema = "analytics")
public class DashboardLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "owner_username", nullable = false)
    private String ownerUsername;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean shared = false;

    /**
     * Array of widget definitions: [{kpiCode, position}, ...].
     */
    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String widgets;

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

    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getShared() { return shared; }
    public void setShared(Boolean shared) { this.shared = shared; }

    public String getWidgets() { return widgets; }
    public void setWidgets(String widgets) { this.widgets = widgets; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
