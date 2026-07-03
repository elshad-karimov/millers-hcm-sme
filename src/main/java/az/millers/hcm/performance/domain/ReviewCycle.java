package az.millers.hcm.performance.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

@Entity
@Table(name = "review_cycle", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class ReviewCycle {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false, length = 24)
    private CycleType cycleType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "self_review_due")
    private LocalDate selfReviewDue;

    @Column(name = "manager_review_due")
    private LocalDate managerReviewDue;

    @Column(name = "final_due")
    private LocalDate finalDue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CycleStatus status;

    // ── HCM_12 M389 — scale/template links + scoping/eligibility (§5.1/§10.2) ──

    /** Rating scale for this cycle (null → tenant default scale). */
    @Column(name = "rating_scale_id")
    private UUID ratingScaleId;

    /** Default review template for this cycle (null → best applicability match). */
    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "legal_entity_id")
    private UUID legalEntityId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "grade_id")
    private UUID gradeId;

    @Column(name = "employee_type", length = 40)
    private String employeeType;

    /** Minimum months of service to be eligible for this cycle (§10.2). */
    @Column(name = "min_service_months")
    private Integer minServiceMonths;

    // ── HCM_12 M395 — §13.4 per-cycle 360° reviewer rules ──

    /** Minimum reviewers required for a complete 360° (null = no minimum). */
    @Column(name = "feedback_min_reviewers")
    private Integer feedbackMinReviewers;

    /** Maximum reviewers allowed per subject (null = unlimited). */
    @Column(name = "feedback_max_reviewers")
    private Integer feedbackMaxReviewers;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rating_scale")
    private Map<String, Object> ratingScale;

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
        if (status == null) status = CycleStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
