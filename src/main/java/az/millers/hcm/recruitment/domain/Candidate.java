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

@Entity
@Table(name = "candidate", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class Candidate {

    @Id
    private UUID id;

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
