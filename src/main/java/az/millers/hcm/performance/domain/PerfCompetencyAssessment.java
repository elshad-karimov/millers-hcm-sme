package az.millers.hcm.performance.domain;

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

/** HCM_12 M393 — per-review competency assessment row (PRD §17). */
@Entity
@Table(name = "perf_competency_assessment", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class PerfCompetencyAssessment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    /** Snapshot of learning.position_competency_requirement.required_proficiency at init. */
    @Column(name = "required_level")
    private Integer requiredLevel;

    @Column(name = "self_level")
    private Integer selfLevel;

    @Column(name = "manager_level")
    private Integer managerLevel;

    @Column(name = "final_level")
    private Integer finalLevel;

    /** §17.3 — required − final; positive = development need. */
    @Column
    private Integer gap;

    @Column(length = 1000)
    private String comment;

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
