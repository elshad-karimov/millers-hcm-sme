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
 * M461 — Employee loan request (ESS submit → eligibility check → workflow → PayrollLoan).
 */
@Entity
@Table(name = "loan_request", schema = "payroll")
public class LoanRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(name = "request_no", nullable = false, unique = true, length = 20)
    private String requestNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "loan_type_id", nullable = false)
    private UUID loanTypeId;

    @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "requested_months", nullable = false)
    private Integer requestedMonths;

    @Column(length = 1000)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanRequestStatus status = LoanRequestStatus.DRAFT;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "eligibility_check_passed")
    private Boolean eligibilityCheckPassed;

    @Column(name = "eligibility_notes", columnDefinition = "text")
    private String eligibilityNotes;

    @Column(name = "payroll_loan_id")
    private UUID payrollLoanId;

    @Column(name = "requested_by", length = 80)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    @Column(name = "approved_by", length = 80)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_reason", columnDefinition = "text")
    private String rejectedReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

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

    public String getRequestNo() {
        return requestNo;
    }

    public void setRequestNo(String requestNo) {
        this.requestNo = requestNo;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public UUID getLoanTypeId() {
        return loanTypeId;
    }

    public void setLoanTypeId(UUID loanTypeId) {
        this.loanTypeId = loanTypeId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public Integer getRequestedMonths() {
        return requestedMonths;
    }

    public void setRequestedMonths(Integer requestedMonths) {
        this.requestedMonths = requestedMonths;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public LoanRequestStatus getStatus() {
        return status;
    }

    public void setStatus(LoanRequestStatus status) {
        this.status = status;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(UUID workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public Boolean getEligibilityCheckPassed() {
        return eligibilityCheckPassed;
    }

    public void setEligibilityCheckPassed(Boolean eligibilityCheckPassed) {
        this.eligibilityCheckPassed = eligibilityCheckPassed;
    }

    public String getEligibilityNotes() {
        return eligibilityNotes;
    }

    public void setEligibilityNotes(String eligibilityNotes) {
        this.eligibilityNotes = eligibilityNotes;
    }

    public UUID getPayrollLoanId() {
        return payrollLoanId;
    }

    public void setPayrollLoanId(UUID payrollLoanId) {
        this.payrollLoanId = payrollLoanId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectedReason() {
        return rejectedReason;
    }

    public void setRejectedReason(String rejectedReason) {
        this.rejectedReason = rejectedReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
