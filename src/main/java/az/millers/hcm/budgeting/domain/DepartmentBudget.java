package az.millers.hcm.budgeting.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * HCM_20 M425 — Department budget (PRD §20.3).
 * Tracks salary/headcount/benefits/training/recruitment/overtime allocations
 * per org unit + cycle. DRAFT → SUBMITTED → APPROVED/REJECTED via workflow.
 */
@Data
@Entity
@Table(name = "department_budget", schema = "budgeting")
public class DepartmentBudget {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    @Column(name = "salary_budget", nullable = false, precision = 14, scale = 2)
    private BigDecimal salaryBudget = BigDecimal.ZERO;

    @Column(name = "headcount_budget", nullable = false)
    private Integer headcountBudget = 0;

    @Column(name = "benefits_budget", nullable = false, precision = 14, scale = 2)
    private BigDecimal benefitsBudget = BigDecimal.ZERO;

    @Column(name = "training_budget", nullable = false, precision = 14, scale = 2)
    private BigDecimal trainingBudget = BigDecimal.ZERO;

    @Column(name = "recruitment_budget", nullable = false, precision = 14, scale = 2)
    private BigDecimal recruitmentBudget = BigDecimal.ZERO;

    @Column(name = "overtime_budget", nullable = false, precision = 14, scale = 2)
    private BigDecimal overtimeBudget = BigDecimal.ZERO;

    @Column(name = "consumed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal consumedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepartmentBudgetStatus status = DepartmentBudgetStatus.DRAFT;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "approved_by", length = 80)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    /**
     * Computed total budget = sum of all line budgets.
     */
    public BigDecimal getTotalBudget() {
        return salaryBudget
                .add(benefitsBudget)
                .add(trainingBudget)
                .add(recruitmentBudget)
                .add(overtimeBudget);
    }
}
