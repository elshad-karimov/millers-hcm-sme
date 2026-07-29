package az.millers.hcm.learning.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * A structured training plan targeting a department, org unit, or the whole
 * company for a given period (M157 / §8.14.2).
 *
 * <p>Plan types: DEPARTMENT, ANNUAL, COMPLIANCE, CAREER_PATH.
 * <p>Lifecycle:  DRAFT → ACTIVE → COMPLETED | ARCHIVED.
 */
@Entity
@Table(name = "training_plan", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class TrainingPlan {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "plan_no", nullable = false, unique = true)
    private String planNo;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /** DEPARTMENT | ANNUAL | COMPLIANCE | CAREER_PATH */
    @Column(name = "plan_type", nullable = false)
    private String planType;

    /** DRAFT | ACTIVE | COMPLETED | ARCHIVED */
    @Column(nullable = false)
    private String status;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "fiscal_year")
    private Short fiscalYear;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "enrolled_count")
    private int enrolledCount;

    @Column(name = "completed_count")
    private int completedCount;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<TrainingPlanItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = "DRAFT";
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
