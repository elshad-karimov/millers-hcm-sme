package az.millers.hcm.performance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

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
import org.hibernate.annotations.TenantId;

/**
 * HCM_12 M388 — tenant-configurable rating scale master (PRD §5.3). Replaces the
 * JSONB rating map embedded on {@link ReviewCycle} as the source of truth (that map is
 * kept for back-compat); §18.3 score-band conversion reads the scale values.
 */
@Entity
@Table(name = "rating_scale", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class RatingScale {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "scale_code", nullable = false, length = 40)
    private String scaleCode;

    @Column(name = "scale_name", nullable = false, length = 160)
    private String scaleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 30)
    private RatingScaleType scaleType = RatingScaleType.NUMERIC_1_5;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    /** The tenant's default scale used when a cycle doesn't pick one explicitly. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

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
