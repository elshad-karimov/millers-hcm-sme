package az.millers.hcm.payroll.domain;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M351 — Salary advance request from employee.
 * Approved advances create a SALARY_ADVANCE deduction in the specified repayment period.
 */
@Entity
@Table(name = "salary_advance", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class SalaryAdvance {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "repayment_year")
    private Integer repaymentYear;

    @Column(name = "repayment_month")
    private Integer repaymentMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SalaryAdvanceStatus status = SalaryAdvanceStatus.PENDING;

    @Column(length = 500)
    private String reason;

    @Column(name = "requested_by", nullable = false, length = 200)
    private String requestedBy;

    @Column(name = "approved_by", length = 200)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "disbursed_at")
    private OffsetDateTime disbursedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
