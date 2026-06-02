package az.millers.hcm.corehr.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.common.expiry.ExpiryTrackable;
import az.millers.hcm.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * One identification document (passport / visa / work permit / etc.) belonging
 * to an employee (M63 / P1-04).
 *
 * <p>Document numbers are AES-256-GCM encrypted in the same column-converter
 * pattern as {@code Employee.nationalId}.
 *
 * <p>Implements {@link ExpiryTrackable} so the M61 {@code ExpiryAlertScheduler}
 * picks up renewals automatically once the matching {@code ExpiryAlertSource}
 * bean is registered.
 */
@Entity
@Table(name = "employee_identification", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeIdentification implements ExpiryTrackable {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private IdentificationDocumentType documentType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "document_number", nullable = false, length = 500)
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "issuing_authority", length = 160)
    private String issuingAuthority;

    /** ISO 3166-1 alpha-2 — same convention as {@code Employee.nationality}. */
    @Column(name = "issuing_country", length = 2)
    private String issuingCountry;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "verified_by", length = 80)
    private String verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "notes", columnDefinition = "text")
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

    // ── ExpiryTrackable ───────────────────────────────────────────────────────

    @Override
    public String getEntityLabel() {
        return documentType == null ? "Identification" : prettyLabel(documentType);
    }

    @Override
    public String getDisplayName() {
        // Last 4 chars of the document number — full plaintext stays in the
        // backend, the alert body only needs enough for the employee to
        // recognise which document is expiring.
        String n = documentNumber;
        if (n == null || n.length() <= 4) return getEntityLabel();
        return getEntityLabel() + " …" + n.substring(n.length() - 4);
    }

    private static String prettyLabel(IdentificationDocumentType t) {
        return switch (t) {
            case NATIONAL_ID       -> "National ID";
            case PASSPORT          -> "Passport";
            case VISA              -> "Visa";
            case WORK_PERMIT       -> "Work Permit";
            case RESIDENCY_PERMIT  -> "Residency Permit";
            case DRIVER_LICENSE    -> "Driver Licence";
        };
    }
}
