package az.millers.hcm.corehr.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

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
 * M262 / PRD §29 — A document the employee is required to submit.
 *
 * <p>Created automatically by the M248 position-profile auto-grant
 * (Phase F.5a) when a position has a REQUIRED_DOCUMENT profile item.
 * Once HR (or future self-service) accepts an attachment, the row's
 * status flips to SATISFIED and the attachment id is linked here.
 */
@Entity
@Table(name = "required_employee_document", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class RequiredEmployeeDocument {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private DocumentRequirementType documentType;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "required_by_date")
    private LocalDate requiredByDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RequiredDocumentStatus status = RequiredDocumentStatus.PENDING;

    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Column(name = "satisfied_at")
    private OffsetDateTime satisfiedAt;

    @Column(name = "satisfied_by", length = 120)
    private String satisfiedBy;

    /** PROFILE_GRANT / MANUAL — where the row came from. */
    @Column(name = "source", nullable = false, length = 32)
    private String source = "MANUAL";

    /** Back-link to the M248 PositionProfileGrant row when source=PROFILE_GRANT. */
    @Column(name = "source_grant_id")
    private UUID sourceGrantId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
