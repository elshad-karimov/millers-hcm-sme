package az.millers.hcm.lifecycle.domain;

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

@Entity
@Table(name = "offboarding_case", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class OffboardingCase {

    @Id
    private UUID id;

    @Column(name = "case_no", nullable = false, unique = true)
    private String caseNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OffboardingSource source;

    @Column(name = "resignation_id")
    private UUID resignationId;

    @Column(name = "termination_id")
    private UUID terminationId;

    @Column(name = "exit_reason", columnDefinition = "text")
    private String exitReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_status", nullable = false, length = 30)
    private OffboardingCaseStatus caseStatus;

    @Column(name = "case_owner", length = 100)
    private String caseOwner;

    @Column(name = "last_working_date")
    private LocalDate lastWorkingDate;

    @Column(name = "access_removal_date")
    private LocalDate accessRemovalDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "checklist_assignment_id")
    private UUID checklistAssignmentId;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "benefits_closure_status", length = 30)
    private String benefitsClosureStatus = "PENDING";

    @Column(name = "benefits_closure_notes", columnDefinition = "text")
    private String benefitsClosureNotes;

    @Column(name = "benefits_closed_at")
    private OffsetDateTime benefitsClosedAt;

    @Column(name = "rehire_eligible")
    private Boolean rehireEligible = true;

    @Column(name = "rehire_eligible_from")
    private java.time.LocalDate rehireEligibleFrom;

    @Column(name = "rehire_ineligible_reason", columnDefinition = "text")
    private String rehireIneligibleReason;

    @Column(name = "replacement_required")
    private Boolean replacementRequired = false;

    @Column(name = "replacement_notes", columnDefinition = "text")
    private String replacementNotes;

    @Column(name = "vacancy_triggered_at")
    private OffsetDateTime vacancyTriggeredAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
