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
import org.hibernate.annotations.TenantId;

/**
 * Categorised HR document record attached to an employee (M169 / PRD §8.1.3).
 *
 * <p>Each row carries a {@link EmployeeDocumentType}, an optional expiry date,
 * an access-restriction flag, and a soft-FK ({@code attachment_id}) to the
 * {@code core_hr.attachment} row that holds the actual binary in MinIO.
 * The attachment may be null when a document is pre-registered before the
 * scan has been uploaded.
 */
@Entity
@Table(name = "employee_document", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeDocument {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private EmployeeDocumentType documentType;

    /** Soft-FK to {@code core_hr.attachment} — null until a file is uploaded. */
    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Column(length = 255)
    private String title;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** M439 — Document category (FK to document_category). */
    @Column(name = "category_id")
    private UUID categoryId;

    /** M439 — Version number (1-based; uploadNewVersion increments). */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /** M439 — Chain to previous version; latest = current (max version). */
    @Column(name = "previous_version_id")
    private UUID previousVersionId;

    /** When {@code true} only HR_ADMIN / SYSTEM_ADMIN may view this row. */
    @Column(nullable = false)
    private boolean restricted;

    @Column(columnDefinition = "text")
    private String notes;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
