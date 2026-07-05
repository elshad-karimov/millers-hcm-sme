package az.millers.hcm.employeerelations.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * M445 — ER investigation interview.
 */
@Entity
@Table(schema = "employee_relations", name = "er_investigation_interview")
public class ErInvestigationInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Column(name = "interviewee_name", nullable = false, length = 255)
    private String intervieweeName;

    @Column(name = "interviewee_role", length = 100)
    private String intervieweeRole;

    @Column(name = "interview_date", nullable = false)
    private LocalDate interviewDate;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

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

    public UUID getInvestigationId() {
        return investigationId;
    }

    public void setInvestigationId(UUID investigationId) {
        this.investigationId = investigationId;
    }

    public String getIntervieweeName() {
        return intervieweeName;
    }

    public void setIntervieweeName(String intervieweeName) {
        this.intervieweeName = intervieweeName;
    }

    public String getIntervieweeRole() {
        return intervieweeRole;
    }

    public void setIntervieweeRole(String intervieweeRole) {
        this.intervieweeRole = intervieweeRole;
    }

    public LocalDate getInterviewDate() {
        return interviewDate;
    }

    public void setInterviewDate(LocalDate interviewDate) {
        this.interviewDate = interviewDate;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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
}
