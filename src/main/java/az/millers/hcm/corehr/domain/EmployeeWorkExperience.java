package az.millers.hcm.corehr.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

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
import org.hibernate.annotations.TenantId;

/**
 * Work experience entry for an employee (M71 / P2-05).
 *
 * <p>{@code lastSalary} is encrypted via the standard AES-256-GCM converter —
 * compensation history from prior employers is sensitive PII even though
 * the values themselves are no longer current.
 *
 * <p>Two flavours via {@link WorkExperienceType}: EXTERNAL is entered at hire
 * from a CV; INTERNAL is auto-populated from {@code lifecycle.contract_change}
 * records in a future enhancement (TODO P2 follow-up).
 */
@Entity
@Table(name = "employee_work_experience", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeWorkExperience {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_type", nullable = false, length = 20)
    private WorkExperienceType experienceType = WorkExperienceType.EXTERNAL;

    @Column(name = "employer_name", nullable = false, length = 200)
    private String employerName;

    @Column(length = 120)
    private String industry;

    @Column(name = "job_title", nullable = false, length = 200)
    private String jobTitle;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "reason_for_leaving", length = 200)
    private String reasonForLeaving;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "last_salary", length = 500)
    private String lastSalary;

    @Column(name = "last_salary_currency", length = 3)
    private String lastSalaryCurrency;

    @Column(columnDefinition = "text")
    private String responsibilities;

    @Column(name = "reference_contact", length = 200)
    private String referenceContact;

    @Column(name = "reference_verified", nullable = false)
    private boolean referenceVerified = false;

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
}
