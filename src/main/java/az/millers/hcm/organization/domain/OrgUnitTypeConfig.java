package az.millers.hcm.organization.domain;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * M143 — runtime-configurable org-unit type definition (§5).
 *
 * <p>The natural PK is the type {@link #code} (upper-case, 1-64 chars).
 * The seven seed rows match the former {@link OrgUnitType} enum constants.
 * Operators can add further types without a code deploy; the Java constants
 * class documents the built-in set but is no longer the source of truth.
 */
@Entity
@Table(name = "org_unit_type", schema = "organization")
@Getter
@Setter
@NoArgsConstructor
public class OrgUnitTypeConfig {

    /** Upper-case code, e.g. "COMPANY", "DEPARTMENT". PK and immutable after creation. */
    @Id
    @Column(length = 64)
    private String code;

    /**
     * Multi-tenancy: org-unit TYPES are a universal taxonomy shared across all
     * tenants (COMPANY/DIVISION/DEPARTMENT/…), so this is intentionally NOT a
     * {@code @TenantId} discriminator entity — every tenant sees the same seeded
     * set. The column stays (defaulted 'default') for schema uniformity; the PK
     * is the code, which is globally unique as befits a shared lookup.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId = "default";

    @Column(nullable = false, length = 200)
    private String label;

    /** Hex colour token for the UI badge, e.g. {@code #1677ff}. */
    @Column(length = 7)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * When {@code false}, this type may only be a leaf — the service rejects
     * attempts to add a child unit beneath it.
     */
    @Column(name = "can_have_children", nullable = false)
    private boolean canHaveChildren = true;

    /**
     * When {@code true}, units of this type may have no parent (root).
     * A unit whose parent is null but whose type has {@code isRootLevel=false}
     * will be rejected by the service.
     */
    @Column(name = "is_root_level", nullable = false)
    private boolean rootLevel;

    /**
     * JSON array of type codes whose units are the only valid parents for
     * units of this type. {@code null} = any parent type allowed.
     *
     * <p>Example: {@code ["BRANCH","DIVISION"]} constrains DEPARTMENT to sit
     * only under BRANCH or DIVISION units.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_parent_types", columnDefinition = "jsonb")
    private String allowedParentTypes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
