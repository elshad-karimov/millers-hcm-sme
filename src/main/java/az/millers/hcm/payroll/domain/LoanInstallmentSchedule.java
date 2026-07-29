package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * M462 — Loan installment schedule (computed at approval, regenerated on settle/reschedule).
 */
@Entity
@Table(name = "loan_installment_schedule", schema = "payroll")
public class LoanInstallmentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(name = "loan_request_id", nullable = false)
    private UUID loanRequestId;

    @Column(name = "payroll_loan_id")
    private UUID payrollLoanId;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_year", nullable = false)
    private Integer dueYear;

    @Column(name = "due_month", nullable = false)
    private Integer dueMonth;

    @Column(name = "installment_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "principal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(name = "remaining_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal remainingBalance;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanInstallmentStatus status = LoanInstallmentStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getLoanRequestId() {
        return loanRequestId;
    }

    public void setLoanRequestId(UUID loanRequestId) {
        this.loanRequestId = loanRequestId;
    }

    public UUID getPayrollLoanId() {
        return payrollLoanId;
    }

    public void setPayrollLoanId(UUID payrollLoanId) {
        this.payrollLoanId = payrollLoanId;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    public void setInstallmentNumber(Integer installmentNumber) {
        this.installmentNumber = installmentNumber;
    }

    public Integer getDueYear() {
        return dueYear;
    }

    public void setDueYear(Integer dueYear) {
        this.dueYear = dueYear;
    }

    public Integer getDueMonth() {
        return dueMonth;
    }

    public void setDueMonth(Integer dueMonth) {
        this.dueMonth = dueMonth;
    }

    public BigDecimal getInstallmentAmount() {
        return installmentAmount;
    }

    public void setInstallmentAmount(BigDecimal installmentAmount) {
        this.installmentAmount = installmentAmount;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    public void setInterestAmount(BigDecimal interestAmount) {
        this.interestAmount = interestAmount;
    }

    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    public void setRemainingBalance(BigDecimal remainingBalance) {
        this.remainingBalance = remainingBalance;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LoanInstallmentStatus getStatus() {
        return status;
    }

    public void setStatus(LoanInstallmentStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
