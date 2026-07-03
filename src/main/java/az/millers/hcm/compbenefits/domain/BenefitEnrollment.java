package az.millers.hcm.compbenefits.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

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

/** Per-employee enrolment in a {@link BenefitPlan} (M108). */
@Entity
@Table(name = "benefit_enrollment", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitEnrollment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ENROLLED;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_tier_code", length = 30)
    private BenefitCoverageTier coverageTierCode;

    @Column(name = "plan_year")
    private Integer planYear;

    /** Snapshot of the employer monthly contribution at enrolment time (from the tier or plan). */
    @Column(name = "employer_contribution", nullable = false, precision = 14, scale = 2)
    private java.math.BigDecimal employerContribution = java.math.BigDecimal.ZERO;

    /** Snapshot of the employee monthly contribution — what the payroll deduction uses (M378). */
    @Column(name = "employee_contribution", nullable = false, precision = 14, scale = 2)
    private java.math.BigDecimal employeeContribution = java.math.BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency = "AZN";

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "dependents_covered", nullable = false)
    private int dependentsCovered = 0;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "enrolled_by", length = 80)
    private String enrolledBy;

    @Column(name = "enrolled_at", nullable = false)
    private OffsetDateTime enrolledAt;

    @Column(name = "terminated_by", length = 80)
    private String terminatedBy;

    @Column(name = "terminated_at")
    private OffsetDateTime terminatedAt;

    @Column(name = "termination_reason", columnDefinition = "text")
    private String terminationReason;

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
        if (enrolledAt == null) enrolledAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
