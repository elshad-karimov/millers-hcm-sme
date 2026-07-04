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
 * HCM_15 M411 — talent review (PRD §15.8). Panel decision per employee per
 * cycle: 9-box placement (performance/potential boxes 1..3), HiPo designation,
 * retention risk + action. Decisions roll onto talent_profile (M409).
 * HR-only visibility (GLOBAL RULE 17).
 */
@Entity
@Table(name = "talent_review", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class TalentReview {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** 1=low, 2=mid, 3=high performance (nullable: may not be placed yet) */
    @Column(name = "performance_box")
    private Integer performanceBox;

    /** 1=low, 2=mid, 3=high potential */
    @Column(name = "potential_box")
    private Integer potentialBox;

    @Column(name = "hipo_decision", nullable = false)
    private boolean hipoDecision = false;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(name = "retention_risk", length = 10)
    private String retentionRisk;

    @Column(name = "retention_action", length = 2000)
    private String retentionAction;

    @Column(length = 2000)
    private String notes;

    @Column(name = "decided_by", length = 80)
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
