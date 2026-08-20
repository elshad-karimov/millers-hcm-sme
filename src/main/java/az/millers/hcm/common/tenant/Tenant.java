package az.millers.hcm.common.tenant;

import java.time.OffsetDateTime;

import az.millers.hcm.config.plan.Plan;
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

/**
 * A tenant in the multi-tenancy registry (Phase 3).
 *
 * <p>Maps a Keycloak realm's JWT issuer ({@code iss} claim) to an internal
 * tenant id. The {@link #id} IS the discriminator value stamped on every
 * {@code @TenantId} entity for rows belonging to this tenant.
 *
 * <p>This is a SYSTEM entity — it is the list <em>of</em> tenants, so it is not
 * itself tenant-scoped (no {@code @TenantId}).
 */
@Entity
@Table(name = "tenant", schema = "config")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    /** Internal tenant id == the tenant_id discriminator value on scoped rows. */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** JWT {@code iss} claim that tokens for this tenant carry. Unique. */
    @Column(name = "issuer_uri", nullable = false, length = 500)
    private String issuerUri;

    /** Keycloak realm name (informational). */
    @Column(name = "realm", length = 200)
    private String realm;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Commercial edition — decides which modules the tenant may use and which
     * plan limits apply. Stored as the {@code Plan} enum name; new tenants in
     * this (SME) edition default to LITE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private Plan plan = Plan.defaultPlan();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    public Tenant(String id, String name, String issuerUri, String realm) {
        this.id = id;
        this.name = name;
        this.issuerUri = issuerUri;
        this.realm = realm;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
