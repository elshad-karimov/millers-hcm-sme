package az.millers.hcm.lifecycle.domain;

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
@Table(name = "exit_interview", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ExitInterview {

    @Id
    private UUID id;

    @Column(name = "termination_id", unique = true)
    private UUID terminationId;

    @Column(name = "case_id", unique = true)
    private UUID caseId;

    @Column(name = "interview_mode", length = 30)
    private String interviewMode;

    @Column(name = "interview_date")
    private java.time.LocalDate interviewDate;

    @Column(name = "follow_up_required")
    private Boolean followUpRequired = false;

    @Column(name = "follow_up_notes", columnDefinition = "text")
    private String followUpNotes;

    @Column(name = "conducted_at")
    private OffsetDateTime conductedAt;

    @Column(name = "conducted_by")
    private String conductedBy;

    @Column(name = "overall_rating")
    private Integer overallRating;

    @Column(name = "would_recommend")
    private Boolean wouldRecommend;

    @Column(name = "reason_for_leaving", columnDefinition = "text")
    private String reasonForLeaving;

    @Column(columnDefinition = "text")
    private String feedback;

    @Column(name = "improvement_suggestions", columnDefinition = "text")
    private String improvementSuggestions;

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
