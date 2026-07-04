package az.millers.hcm.staffing.domain;

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

/**
 * M127 — one competency requirement on a position.
 *
 * <p>Carries the target proficiency level (1..5), whether the competency
 * is mandatory (a missing mandatory triggers a BLOCKER gap; a missing
 * optional triggers MINOR), and free-text notes. The
 * {@code (position_id, competency_id)} unique constraint guarantees one
 * row per (position, competency) so HR can't accidentally duplicate.
 */
@Entity
@Table(name = "position_required_competency", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class PositionRequiredCompetency {

    @Id
    private UUID id;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    @Column(name = "required_level", nullable = false)
    private int requiredLevel;

    @Column(nullable = false)
    private boolean mandatory = true;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 160)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 160)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
