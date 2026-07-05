package az.millers.hcm.corehr.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "signature_request", schema = "core_hr")
public class SignatureRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "employee_document_id")
    private UUID employeeDocumentId;

    @Column(name = "letter_request_id")
    private UUID letterRequestId;

    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SignatureRequestStatus status = SignatureRequestStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private SignatureProvider provider = SignatureProvider.INTERNAL;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getEmployeeDocumentId() {
        return employeeDocumentId;
    }

    public void setEmployeeDocumentId(UUID employeeDocumentId) {
        this.employeeDocumentId = employeeDocumentId;
    }

    public UUID getLetterRequestId() {
        return letterRequestId;
    }

    public void setLetterRequestId(UUID letterRequestId) {
        this.letterRequestId = letterRequestId;
    }

    public UUID getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(UUID attachmentId) {
        this.attachmentId = attachmentId;
    }

    public SignatureRequestStatus getStatus() {
        return status;
    }

    public void setStatus(SignatureRequestStatus status) {
        this.status = status;
    }

    public SignatureProvider getProvider() {
        return provider;
    }

    public void setProvider(SignatureProvider provider) {
        this.provider = provider;
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

    public enum SignatureRequestStatus {
        PENDING, COMPLETED, CANCELLED
    }

    public enum SignatureProvider {
        INTERNAL, DOCUSIGN
    }
}
