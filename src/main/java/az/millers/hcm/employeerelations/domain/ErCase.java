package az.millers.hcm.employeerelations.domain;

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
 * M445 — ER cases (grievances, complaints, investigations, disciplinary links).
 * Confidential cases visible only to HR_ADMIN + owner.
 */
@Entity
@Table(schema = "employee_relations", name = "er_case")
public class ErCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "case_no", nullable = false, unique = true, length = 20)
    private String caseNo;

    @Column(name = "employee_id")
    private UUID employeeId; // nullable for anonymous complaints

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, length = 60)
    private ErCaseType caseType;

    @Column(name = "category", length = 60)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private ErCaseSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ErCaseStatus status = ErCaseStatus.OPEN;

    @Column(name = "owner_username", length = 255)
    private String ownerUsername;

    @Column(name = "is_confidential", nullable = false)
    private Boolean isConfidential = false;

    @Column(name = "legal_hold", nullable = false)
    private Boolean legalHold = false;

    @Column(name = "linked_disciplinary_id")
    private UUID linkedDisciplinaryId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "outcome", columnDefinition = "TEXT")
    private String outcome;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by", length = 255)
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

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public ErCaseType getCaseType() {
        return caseType;
    }

    public void setCaseType(ErCaseType caseType) {
        this.caseType = caseType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public ErCaseSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(ErCaseSeverity severity) {
        this.severity = severity;
    }

    public ErCaseStatus getStatus() {
        return status;
    }

    public void setStatus(ErCaseStatus status) {
        this.status = status;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public Boolean getIsConfidential() {
        return isConfidential;
    }

    public void setIsConfidential(Boolean isConfidential) {
        this.isConfidential = isConfidential;
    }

    public Boolean getLegalHold() {
        return legalHold;
    }

    public void setLegalHold(Boolean legalHold) {
        this.legalHold = legalHold;
    }

    public UUID getLinkedDisciplinaryId() {
        return linkedDisciplinaryId;
    }

    public void setLinkedDisciplinaryId(UUID linkedDisciplinaryId) {
        this.linkedDisciplinaryId = linkedDisciplinaryId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
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
