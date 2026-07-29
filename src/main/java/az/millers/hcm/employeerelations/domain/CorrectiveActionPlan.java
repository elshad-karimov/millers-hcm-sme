package az.millers.hcm.employeerelations.domain;

import java.time.LocalDate;
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
 * M447 — Corrective action plan.
 */
@Entity
@Table(schema = "employee_relations", name = "corrective_action_plan")
public class CorrectiveActionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "er_case_id")
    private UUID erCaseId;

    @Column(name = "disciplinary_action_id")
    private UUID disciplinaryActionId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "action_required", nullable = false, length = 2000)
    private String actionRequired;

    @Column(name = "responsible_username", nullable = false, length = 255)
    private String responsibleUsername;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CorrectiveActionStatus status = CorrectiveActionStatus.OPEN;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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

    public UUID getErCaseId() {
        return erCaseId;
    }

    public void setErCaseId(UUID erCaseId) {
        this.erCaseId = erCaseId;
    }

    public UUID getDisciplinaryActionId() {
        return disciplinaryActionId;
    }

    public void setDisciplinaryActionId(UUID disciplinaryActionId) {
        this.disciplinaryActionId = disciplinaryActionId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public String getActionRequired() {
        return actionRequired;
    }

    public void setActionRequired(String actionRequired) {
        this.actionRequired = actionRequired;
    }

    public String getResponsibleUsername() {
        return responsibleUsername;
    }

    public void setResponsibleUsername(String responsibleUsername) {
        this.responsibleUsername = responsibleUsername;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public CorrectiveActionStatus getStatus() {
        return status;
    }

    public void setStatus(CorrectiveActionStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
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
