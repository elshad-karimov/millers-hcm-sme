package az.millers.hcm.performance.domain;

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

/** HCM_12 M398 — performance improvement plan (PRD §20). */
@Entity
@Table(name = "performance_pip", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class PerformancePip {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "review_id")
    private UUID reviewId;

    @Column(name = "manager_owner_id")
    private UUID managerOwnerId;

    @Column(name = "hr_owner", length = 80)
    private String hrOwner;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "issue_description", columnDefinition = "text")
    private String issueDescription;

    @Column(columnDefinition = "text")
    private String objectives;

    @Column(name = "success_criteria", columnDefinition = "text")
    private String successCriteria;

    @Column(name = "support_actions", columnDefinition = "text")
    private String supportActions;

    @Column(columnDefinition = "text")
    private String consequences;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** DRAFT | ACTIVE | ACKNOWLEDGED | IN_PROGRESS | EXTENDED | COMPLETED_SUCCESS | FAILED | CANCELLED */
    @Column(nullable = false, length = 24)
    private String status = "DRAFT";

    /** §20.4 outcome — IMPROVED | EXTENDED | ROLE_CHANGE | TRAINING_REQUIRED | DISCIPLINARY | TERMINATION_RECOMMENDED */
    @Column(length = 30)
    private String outcome;

    @Column(name = "outcome_notes", length = 2000)
    private String outcomeNotes;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "acknowledged_comments", length = 2000)
    private String acknowledgedComments;

    @Column(name = "created_by", length = 80)
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
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
