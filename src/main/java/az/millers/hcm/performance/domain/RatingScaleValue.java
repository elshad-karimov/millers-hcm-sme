package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * HCM_12 M388 — one step of a {@link RatingScale}: value + label (+ optional §18.3
 * score band min/max percentage used for numeric-score → rating-label conversion).
 */
@Entity
@Table(name = "rating_scale_value", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class RatingScaleValue {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "scale_id", nullable = false)
    private UUID scaleId;

    @Column(name = "value_order", nullable = false)
    private int valueOrder;

    @Column(name = "rating_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal ratingValue;

    @Column(name = "rating_label", nullable = false, length = 120)
    private String ratingLabel;

    @Column(length = 300)
    private String description;

    /** Score band lower bound (0–100) for §18.3 conversion; nullable when unused. */
    @Column(name = "min_percentage", precision = 5, scale = 2)
    private BigDecimal minPercentage;

    /** Score band upper bound (0–100). */
    @Column(name = "max_percentage", precision = 5, scale = 2)
    private BigDecimal maxPercentage;

    @Column(name = "color_code", length = 20)
    private String colorCode;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
