package az.millers.hcm.compensation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M361 — Salary change request + approval workflow.
 * A salary change must be APPROVED before it flows to EmployeeCompensation.
 */
@Entity
@Table(name = "salary_change_request", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class SalaryChangeRequest {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "current_salary", precision = 14, scale = 2)
    private BigDecimal currentSalary;

    @Column(name = "proposed_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal proposedSalary;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "change_reason_id")
    private UUID changeReasonId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "increase_pct", precision = 6, scale = 2)
    private BigDecimal increasePct;

    @Column(name = "outside_band", nullable = false)
    private Boolean outsideBand = false;

    @Column(name = "above_threshold", nullable = false)
    private Boolean aboveThreshold = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SalaryChangeStatus status = SalaryChangeStatus.DRAFT;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "requested_by", length = 200)
    private String requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(length = 1000)
    private String note;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
