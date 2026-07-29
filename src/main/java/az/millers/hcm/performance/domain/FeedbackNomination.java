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
import org.hibernate.annotations.TenantId;

/**
 * HCM_12 M395 — a 360° reviewer nomination (PRD §13.2). Class named
 * FeedbackNomination (table {@code feedback_request}) to avoid clashing with the
 * pre-existing FeedbackRequest DTO. NOMINATED → APPROVED → COMPLETED, or
 * DECLINED / CANCELLED; on completion {@code feedbackId} links the submitted
 * performance.feedback row.
 */
@Entity
@Table(name = "feedback_request", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class FeedbackNomination {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "subject_employee_id", nullable = false)
    private UUID subjectEmployeeId;

    @Column(name = "reviewer_employee_id", nullable = false)
    private UUID reviewerEmployeeId;

    /** Reuses FeedbackRelationship values (PEER, DIRECT_REPORT, MANAGER, …). */
    @Column(nullable = false, length = 32)
    private String relationship;

    @Column(name = "questionnaire_id")
    private UUID questionnaireId;

    /** Anonymous by default (gap-check decision → §13.4). */
    @Column(nullable = false)
    private boolean anonymous = true;

    /** NOMINATED | APPROVED | DECLINED | COMPLETED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "NOMINATED";

    @Column(name = "nominated_by", length = 80)
    private String nominatedBy;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    @Column(name = "feedback_id")
    private UUID feedbackId;

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
