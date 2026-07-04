package az.millers.hcm.performance.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HCM_13 M403 — business goal-type catalog entry (PRD 13 §4). */
@Entity
@Table(name = "goal_type", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class GoalType {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    /** Pre-selects the goal category — COMPANY | DEPARTMENT | TEAM | INDIVIDUAL | DEVELOPMENT. */
    @Column(name = "default_category", nullable = false, length = 24)
    private String defaultCategory = "INDIVIDUAL";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
