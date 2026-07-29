package az.millers.hcm.compbenefits.domain;

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
import org.hibernate.annotations.TenantId;

/**
 * HCM_11 M373 — Tenant-configurable benefit category master (PRD §2).
 *
 * <p>Replaces M108's hard-coded {@code benefit_type} enum as the source of truth
 * for classifying plans. Seeded with 8 AZ defaults (Health, Life, Pension, Meal,
 * Transport, Housing, Mobile, Wellness); tenants may add/deactivate their own.
 */
@Entity
@Table(name = "benefit_category", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitCategory {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Employer-paid portion is a taxable benefit-in-kind (e.g. meal/transport allowances). */
    @Column(nullable = false)
    private boolean taxable = false;

    /** Plans in this category require an external provider/vendor (M374). */
    @Column(name = "requires_provider", nullable = false)
    private boolean requiresProvider = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;

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
