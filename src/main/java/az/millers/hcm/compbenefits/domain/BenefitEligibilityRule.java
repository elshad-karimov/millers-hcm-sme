package az.millers.hcm.compbenefits.domain;

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
import org.hibernate.annotations.TenantId;

/**
 * HCM_11 M375 — a single eligibility rule for a benefit plan: a set of AND-ed
 * conditions (a null column means "any"). A plan with no rules is open to all;
 * otherwise an employee is eligible if ANY active rule matches (OR-of-rows).
 * Enforced at enrolment time (M376). Soft references (no FK) to core_hr / staffing.
 */
@Entity
@Table(name = "benefit_eligibility_rule", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitEligibilityRule {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "employment_type", length = 40)
    private String employmentType;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "grade_id")
    private UUID gradeId;

    @Column(name = "employee_category", length = 40)
    private String employeeCategory;

    @Column(name = "min_service_months")
    private Integer minServiceMonths;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
