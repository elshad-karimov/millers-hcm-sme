package az.millers.hcm.performance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HCM_16 M413 — critical position (PRD §16.2). Identifies positions requiring
 * succession planning: high criticality, replacement difficulty, vacancy risk.
 * Linked to staffing.position (not JPA FK to avoid bidirectional coupling).
 */
@Entity
@Table(name = "critical_position", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class CriticalPosition {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "criticality_reason", length = 1000)
    private String criticalityReason;

    /** LOW | MEDIUM | HIGH */
    @Column(name = "replacement_difficulty", length = 10)
    private String replacementDifficulty;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(name = "vacancy_risk", length = 10)
    private String vacancyRisk;

    @Column(name = "succession_required", nullable = false)
    private boolean successionRequired = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
