package az.millers.hcm.recruitment.domain;

import java.math.BigDecimal;
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

@Entity
@Table(name = "candidate", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class Candidate {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "candidate_no", nullable = false, unique = true)
    private String candidateNo;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "middle_name")
    private String middleName;

    private String email;
    private String phone;

    @Enumerated(EnumType.STRING)
    private CandidateSource source;

    @Column(name = "cv_url")
    private String cvUrl;

    @Column(name = "experience_years", precision = 5, scale = 2)
    private BigDecimal experienceYears;

    @Column(name = "expected_salary", precision = 12, scale = 2)
    private BigDecimal expectedSalary;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(columnDefinition = "text")
    private String skills;

    @Column(columnDefinition = "text")
    private String notes;

    /** M87 — talent-pool standing for CRM filtering. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "pool_status", nullable = false, length = 20)
    private CandidatePoolStatus poolStatus = CandidatePoolStatus.ACTIVE;

    /** M87 — last outreach / response for stale-pool reports. */
    @Column(name = "last_contacted_at")
    private OffsetDateTime lastContactedAt;

    /** M280 — when the candidate accepted the privacy consent (PRD §46). */
    @Column(name = "consent_at")
    private OffsetDateTime consentAt;

    /** M280 — which consent text version they accepted. */
    @Column(name = "consent_version", length = 20)
    private String consentVersion;

    /**
     * M293 — set when this candidate has been merged into another (the
     * surviving master). Non-null = retired; excluded from active lists
     * and the duplicate scan.
     */
    @Column(name = "merged_into_id")
    private UUID mergedIntoId;

    /**
     * M294 — set when this candidate's PII was scrubbed under the consent
     * retention policy (PRD §46/§47). Non-null = anonymised; excluded from
     * future sweeps and the duplicate scan.
     */
    @Column(name = "anonymized_at")
    private OffsetDateTime anonymizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (currency == null) currency = "AZN";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
