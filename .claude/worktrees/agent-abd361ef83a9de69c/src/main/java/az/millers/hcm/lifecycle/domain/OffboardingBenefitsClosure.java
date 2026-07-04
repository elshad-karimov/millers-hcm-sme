package az.millers.hcm.lifecycle.domain;

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

@Entity
@Table(name = "offboarding_benefits_closure", schema = "lifecycle")
@Getter @Setter @NoArgsConstructor
public class OffboardingBenefitsClosure {

    @Id private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "benefit_type", nullable = false, length = 60)
    private String benefitType;

    @Column(length = 200)
    private String description;

    @Column(name = "closure_status", nullable = false, length = 30)
    private String closureStatus = "PENDING";

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(length = 120)
    private String provider;

    @Column(length = 120)
    private String reference;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now; updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
