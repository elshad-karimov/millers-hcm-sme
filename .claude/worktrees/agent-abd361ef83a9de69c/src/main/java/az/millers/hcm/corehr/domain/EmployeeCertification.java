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
 * External professional certification or licence held by an employee
 * (M65 / P1-13).
 *
 * <p>Distinct from {@code learning.certificate} which only records
 * LMS-course-completion certificates. This table covers credentials granted
 * by an external authority (medical board, PMI, driver licensing agency, etc.).
 *
 * <p>{@code license_number} is AES-256-GCM encrypted via the existing
 * {@link EncryptedStringConverter}. Implements {@link ExpiryTrackable} so the
 * single M61 scheduler picks up expiry alerts via the new
 * {@code EmployeeCertificationExpirySource} bean.
 */
@Entity
@Table(name = "employee_certification", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeCertification implements ExpiryTrackable {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "certification_name", nullable = false, length = 200)
    private String certificationName;

    @Column(name = "issuing_authority", length = 200)
    private String issuingAuthority;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "license_number", length = 500)
    private String licenseNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Soft FK — staffing.position lives in another schema. Null = optional. */
    @Column(name = "required_for_position_id")
    private UUID requiredForPositionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "verified_by", length = 80)
    private String verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

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

    // ── ExpiryTrackable ───────────────────────────────────────────────────────

    @Override public String getEntityLabel() { return "Certification"; }

    @Override
    public String getDisplayName() {
        return certificationName == null ? "Certification" : certificationName;
    }
}
