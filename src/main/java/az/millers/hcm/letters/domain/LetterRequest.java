package az.millers.hcm.letters.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * One employee request for a letter (M77 / P2-17). Lifecycle managed by
 * {@link LetterStatus} — auto-approve templates jump straight to ISSUED;
 * the rest go through the LETTER_REQUEST_APPROVAL workflow.
 */
@Entity
@Table(name = "letter_request", schema = "hr_letters")
@Getter
@Setter
@NoArgsConstructor
public class LetterRequest {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "request_no", nullable = false, unique = true, length = 20)
    private String requestNo;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(length = 500)
    private String purpose;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields_json", columnDefinition = "jsonb")
    private JsonNode customFieldsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LetterStatus status = LetterStatus.PENDING;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "rendered_body", columnDefinition = "text")
    private String renderedBody;

    /** Soft FK to {@code attachment.attachment.id} when output_format=HTML/PDF. */
    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    // ── M139 — Phase 2 fields ───────────────────────────────────────────

    /** MinIO URL of the rendered PDF. Null until the renderer produces one. */
    @Column(name = "rendered_pdf_url", length = 500)
    private String renderedPdfUrl;

    /**
     * Opaque 32-char random token. Printed as a QR code on the PDF and
     * passed back via {@code /api/public/letters/verify/{token}} to
     * confirm authenticity without exposing PII. Set at render time.
     */
    @Column(name = "verification_token", length = 64)
    private String verificationToken;

    /** Last time someone hit the public verify endpoint with this token. */
    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    /** Name of the signing officer (printed on the PDF signature line). */
    @Column(name = "signed_by", length = 160)
    private String signedBy;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    /** ISO 639-1 lowercase — the variant the renderer actually used. */
    @Column(name = "language", length = 2)
    private String language;

    @Column(name = "decided_by", length = 80)
    private String decidedBy;

    @Column(name = "decision_comment", columnDefinition = "text")
    private String decisionComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (requestedAt == null) requestedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
