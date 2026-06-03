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

/**
 * One scheduled interview session against an Application (M85). Carries
 * the weighted-average overall score on finalize and the interviewer's
 * qualitative {@link InterviewRecommendation}.
 */
@Entity
@Table(name = "interview", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class Interview {

    @Id
    private UUID id;

    @Column(name = "interview_no", nullable = false, unique = true, length = 20)
    private String interviewNo;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "kit_id", nullable = false)
    private UUID kitId;

    /** Soft FK to {@code core_hr.employee}. */
    @Column(name = "interviewer_employee_id", nullable = false)
    private UUID interviewerEmployeeId;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    /** Weighted average computed on finalize. 0..5, two decimals. */
    @Column(name = "overall_score", precision = 4, scale = 2)
    private BigDecimal overallScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InterviewRecommendation recommendation;

    @Column(name = "overall_comment", columnDefinition = "text")
    private String overallComment;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

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
