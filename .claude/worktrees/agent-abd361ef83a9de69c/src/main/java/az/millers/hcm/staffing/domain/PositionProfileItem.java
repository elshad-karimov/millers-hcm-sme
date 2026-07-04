package az.millers.hcm.staffing.domain;

import java.math.BigDecimal;
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

/**
 * One line of a position's profile (M248). Each row defines one thing
 * a position requires / provides — an allowance, a required document,
 * a mandatory training, an approval-limit, etc.
 */
@Entity
@Table(name = "position_profile_item", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class PositionProfileItem {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 32)
    private ProfileItemType itemType;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "value_amount", precision = 14, scale = 2)
    private BigDecimal valueAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "reference_code", length = 120)
    private String referenceCode;

    @Column(name = "notes")
    private String notes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
