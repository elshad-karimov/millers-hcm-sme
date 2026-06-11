package az.millers.hcm.recruitment.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "application", schema = "recruitment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"vacancy_id", "candidate_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Application {

    @Id
    private UUID id;

    @Column(name = "application_no", nullable = false, unique = true)
    private String applicationNo;

    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false)
    private ApplicationStage currentStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "created_employee_id")
    private UUID createdEmployeeId;

    /** M280 — which channel/language posting drove this application (PRD §44). */
    @Column(name = "posting_id")
    private UUID postingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

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
