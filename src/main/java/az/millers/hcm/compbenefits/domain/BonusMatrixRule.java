package az.millers.hcm.compbenefits.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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

@Entity
@Table(name = "bonus_matrix_rule", schema = "comp_benefits")
@Getter
@Setter
@NoArgsConstructor
public class BonusMatrixRule {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "match_recommendation", length = 40)
    private String matchRecommendation;

    @Column(name = "min_rating", precision = 4, scale = 2)
    private BigDecimal minRating;

    @Column(name = "max_rating", precision = 4, scale = 2)
    private BigDecimal maxRating;

    @Column(name = "bonus_percent", precision = 5, scale = 2)
    private BigDecimal bonusPercent;

    @Column(name = "flat_amount", precision = 14, scale = 2)
    private BigDecimal flatAmount;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "max_amount", precision = 14, scale = 2)
    private BigDecimal maxAmount;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
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
