package az.millers.hcm.lifecycle.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.common.expiry.ExpiryTrackable;
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

/**
 * Mid-point or final probation review milestone (M73 / P2-01).
 *
 * <p>Implements {@link ExpiryTrackable} so the M61 scheduler can fire
 * reminder alerts at {30, 15, 7, 0} days before the scheduled review date.
 * The expiry-date projection is {@link #getScheduledDate()} when the review
 * is still SCHEDULED; once COMPLETED or CANCELLED the source filters it out.
 */
@Entity
@Table(name = "probation_review", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ProbationReview implements ExpiryTrackable {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 20)
    private ProbationReviewType reviewType;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "manager_employee_id")
    private UUID managerEmployeeId;

    @Column(name = "reviewer_employee_id")
    private UUID reviewerEmployeeId;

    @Column(name = "manager_feedback", columnDefinition = "text")
    private String managerFeedback;

    @Column(name = "manager_rating")
    private Integer managerRating;

    @Column(name = "hr_feedback", columnDefinition = "text")
    private String hrFeedback;

    @Column(name = "hr_rating")
    private Integer hrRating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProbationReviewStatus status = ProbationReviewStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProbationOutcome outcome;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

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

    @Override
    public LocalDate getExpiryDate() {
        return scheduledDate;
    }

    @Override
    public String getEntityLabel() {
        return reviewType == ProbationReviewType.FINAL
                ? "Final probation review"
                : "Mid-point probation review";
    }

    @Override
    public String getDisplayName() {
        // Reviews don't carry a human-readable code — the type + date is what
        // HR recognises. The alert body already includes the expiry date so
        // we just label it by type here.
        return getEntityLabel();
    }
}
