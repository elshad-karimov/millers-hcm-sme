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
 * M352 — Employee payroll loan with fixed monthly installments.
 * PayrollEngine auto-creates LOAN_INSTALLMENT deductions until fully repaid.
 */
@Entity
@Table(name = "payroll_loan", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class PayrollLoan {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "principal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "monthly_installment", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyInstallment;

    @Column(name = "outstanding_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingBalance;

    @Column(name = "amount_repaid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRepaid = BigDecimal.ZERO;

    @Column(name = "start_deduction_year", nullable = false)
    private int startDeductionYear;

    @Column(name = "start_deduction_month", nullable = false)
    private int startDeductionMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayrollLoanStatus status = PayrollLoanStatus.PENDING;

    @Column(length = 500)
    private String description;

    @Column(name = "approved_by", length = 200)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
