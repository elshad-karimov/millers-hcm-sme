package az.millers.hcm.performance.domain;

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

/** HCM_12 M391 — OKR objective; parent link = alignment map (PRD §5.6 / §8). */
@Entity
@Table(name = "okr_objective", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class OkrObjective {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** COMPANY | LEGAL_ENTITY | BUSINESS_UNIT | DEPARTMENT | TEAM | INDIVIDUAL */
    @Column(name = "okr_level", nullable = false, length = 20)
    private String okrLevel;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "owner_employee_id")
    private UUID ownerEmployeeId;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "legal_entity_id")
    private UUID legalEntityId;

    @Column(name = "cycle_id")
    private UUID cycleId;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** DRAFT | ACTIVE | CLOSED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** Weighted average of KR progress — recomputed on every check-in. */
    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    /** HIGH | MEDIUM | LOW */
    @Column(length = 10)
    private String confidence;

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
