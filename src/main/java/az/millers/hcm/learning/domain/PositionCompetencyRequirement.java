package az.millers.hcm.learning.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Maps a {@code staffing.position} to the competency levels required for an
 * employee in that position.  Used by {@link az.millers.hcm.learning.service.GapAnalysisService}
 * to compute per-employee skill gaps and surface course recommendations.
 */
@Entity
@Table(name = "position_competency_requirement", schema = "learning")
@IdClass(PositionCompetencyRequirementId.class)
@Getter
@Setter
@NoArgsConstructor
public class PositionCompetencyRequirement {

    /** Soft reference to {@code staffing.position.id} — no FK by design. */
    @Id
    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "competency_id", nullable = false)
    private Competency competency;

    /** Required proficiency on a 1–5 scale (1 = Awareness, 5 = Expert). */
    @Column(name = "required_proficiency", nullable = false)
    private int requiredProficiency;

    /**
     * Whether this competency is mandatory for the position (V307). A missing
     * mandatory competency is a blocking gap; a missing optional one is minor.
     * Defaults to {@code true} so requirements created via the learning API stay
     * mandatory unless HR explicitly relaxes them.
     */
    @Column(nullable = false)
    private boolean mandatory = true;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
