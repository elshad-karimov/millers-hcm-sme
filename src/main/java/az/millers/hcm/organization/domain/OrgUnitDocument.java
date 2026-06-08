package az.millers.hcm.organization.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import az.millers.hcm.common.expiry.ExpiryTrackable;

/**
 * M147 / §31 — document attached to an org unit (licences, permits,
 * certificates, agreements, etc.).
 *
 * <p>When {@link #expiryDate} is non-null the entity implements
 * {@link ExpiryTrackable} and is automatically picked up by the shared
 * {@link az.millers.hcm.common.expiry.ExpiryAlertScheduler} via
 * {@link az.millers.hcm.organization.service.OrgUnitDocumentExpirySource}.
 * No scheduler changes are needed.
 */
@Entity
@Table(name = "org_unit_document", schema = "organization")
@Getter
@Setter
@NoArgsConstructor
public class OrgUnitDocument implements ExpiryTrackable {

    @Id
    private UUID id;

    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "doc_type", length = 64)
    private String docType;

    @Column(name = "document_ref", length = 400)
    private String documentRef;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * Soft FK to {@code core_hr.employee}. The employee who is responsible
     * for this document and will receive expiry-alert notifications.
     * Typically the org unit's HRBP.
     */
    @Column(name = "responsible_employee_id")
    private UUID responsibleEmployeeId;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

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

    // ── ExpiryTrackable ────────────────────────────────────────────────────

    @Override
    public UUID getEmployeeId() {
        return responsibleEmployeeId;
    }

    @Override
    public String getEntityLabel() {
        return docType != null ? docType : "Org document";
    }

    @Override
    public String getDisplayName() {
        return title;
    }
}
