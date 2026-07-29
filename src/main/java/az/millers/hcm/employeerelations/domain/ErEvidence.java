package az.millers.hcm.employeerelations.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * M445 — ER evidence (attachment soft FK).
 */
@Entity
@Table(schema = "employee_relations", name = "er_evidence")
public class ErEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "attachment_id")
    private UUID attachmentId; // soft FK to core_hr.attachment

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

    @Column(name = "uploaded_by", nullable = false, length = 255)
    private String uploadedBy;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(UUID attachmentId) {
        this.attachmentId = attachmentId;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(OffsetDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
