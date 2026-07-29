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
import org.hibernate.annotations.TenantId;

/** HCM_12 M399 — development plan (PRD §21). */
@Entity
@Table(name = "development_plan", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class DevelopmentPlan {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "review_id")
    private UUID reviewId;

    /** Optional linked competency gap (learning.competency — M393 feeds this). */
    @Column(name = "competency_id")
    private UUID competencyId;

    @Column(name = "career_goal", length = 300)
    private String careerGoal;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "owner_employee_id")
    private UUID ownerEmployeeId;

    /** DRAFT | ACTIVE | IN_PROGRESS | COMPLETED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    /** Done actions / live actions × 100 — recomputed on action change. */
    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Column(name = "manager_comments", length = 2000)
    private String managerComments;

    @Column(name = "employee_comments", length = 2000)
    private String employeeComments;

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
