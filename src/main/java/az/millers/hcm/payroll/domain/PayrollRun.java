package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "payroll_run", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class PayrollRun {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "run_no", nullable = false, unique = true)
    private String runNo;

    /** REGULAR (default), OFF_CYCLE (employee subset), or FINAL_SETTLEMENT (one per terminated employee). */
    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 30)
    private RunType runType = RunType.REGULAR;

    /** Set for FINAL_SETTLEMENT runs; links back to the originating termination. */
    @Column(name = "termination_id")
    private UUID terminationId;

    /** Optional description for the run (useful for off-cycle or special runs). */
    @Column(length = 500)
    private String description;

    /** For OFF_CYCLE runs: the specific employee IDs to include. Null/empty for REGULAR runs. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "employee_ids", columnDefinition = "uuid[]")
    private List<UUID> employeeIds;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(nullable = false, length = 8)
    private String jurisdiction;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollRunStatus status;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "total_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_income_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalIncomeTax = BigDecimal.ZERO;

    @Column(name = "total_dsmf_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDsmfEmployee = BigDecimal.ZERO;

    @Column(name = "total_dsmf_employer", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDsmfEmployer = BigDecimal.ZERO;

    @Column(name = "total_mmi_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalMmiEmployee = BigDecimal.ZERO;

    @Column(name = "total_mmi_employer", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalMmiEmployer = BigDecimal.ZERO;

    @Column(name = "total_unempl_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalUnemplEmployee = BigDecimal.ZERO;

    @Column(name = "total_unempl_employer", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalUnemplEmployer = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    /**
     * Combined taxable + non-taxable allowance spend across the run.
     * Populated by {@link az.millers.hcm.payroll.service.PayrollEngine}
     * (M41 — allowances → payroll integration).
     */
    @Column(name = "total_allowance", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAllowance = BigDecimal.ZERO;

    @Column(name = "employee_count", nullable = false)
    private int employeeCount;

    @Column(name = "calculated_at")
    private OffsetDateTime calculatedAt;

    @Column(name = "calculated_by")
    private String calculatedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "paid_by")
    private String paidBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (currency == null) currency = "AZN";
        if (jurisdiction == null) jurisdiction = "AZ";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
