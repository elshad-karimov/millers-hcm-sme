package az.millers.hcm.lifecycle.domain;

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

/**
 * M458 — Asset damage/loss case with optional deduction (via PayrollDeduction).
 * deduction_amount and deduction_proposed are HR/Finance-only (confidential).
 */
@Entity
@Table(name = "asset_damage_loss_case", schema = "lifecycle")
public class AssetDamageLossCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";

    @Column(name = "case_no", nullable = false, unique = true, length = 20)
    private String caseNo;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, length = 20)
    private AssetDamageLossCaseType caseType;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "estimated_amount", precision = 12, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(name = "deduction_proposed", nullable = false)
    private Boolean deductionProposed = false;

    @Column(name = "deduction_amount", precision = 12, scale = 2)
    private BigDecimal deductionAmount;

    @Column(name = "payroll_deduction_id")
    private UUID payrollDeductionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetDamageLossCaseStatus status = AssetDamageLossCaseStatus.OPEN;

    @Column(name = "reported_by", length = 80)
    private String reportedBy;

    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt = OffsetDateTime.now();

    @Column(name = "approved_by", length = 80)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(columnDefinition = "text")
    private String notes;

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

    public String getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public AssetDamageLossCaseType getCaseType() {
        return caseType;
    }

    public void setCaseType(AssetDamageLossCaseType caseType) {
        this.caseType = caseType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getEstimatedAmount() {
        return estimatedAmount;
    }

    public void setEstimatedAmount(BigDecimal estimatedAmount) {
        this.estimatedAmount = estimatedAmount;
    }

    public Boolean getDeductionProposed() {
        return deductionProposed;
    }

    public void setDeductionProposed(Boolean deductionProposed) {
        this.deductionProposed = deductionProposed;
    }

    public BigDecimal getDeductionAmount() {
        return deductionAmount;
    }

    public void setDeductionAmount(BigDecimal deductionAmount) {
        this.deductionAmount = deductionAmount;
    }

    public UUID getPayrollDeductionId() {
        return payrollDeductionId;
    }

    public void setPayrollDeductionId(UUID payrollDeductionId) {
        this.payrollDeductionId = payrollDeductionId;
    }

    public AssetDamageLossCaseStatus getStatus() {
        return status;
    }

    public void setStatus(AssetDamageLossCaseStatus status) {
        this.status = status;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public OffsetDateTime getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(OffsetDateTime reportedAt) {
        this.reportedAt = reportedAt;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
