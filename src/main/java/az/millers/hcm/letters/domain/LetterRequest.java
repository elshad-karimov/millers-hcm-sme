package az.millers.hcm.letters.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

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
    private Object customFieldsJson;

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
