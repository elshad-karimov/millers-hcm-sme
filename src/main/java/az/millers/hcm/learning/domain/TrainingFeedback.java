package az.millers.hcm.learning.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HCM_14 M408 — post-training feedback, anonymous by default (PRD 14 §20). */
@Entity
@Table(name = "training_feedback", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class TrainingFeedback {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "overall_rating", nullable = false)
    private int overallRating;

    @Column(name = "content_rating")
    private Integer contentRating;

    @Column(name = "instructor_rating")
    private Integer instructorRating;

    @Column(length = 2000)
    private String comment;

    @Column(nullable = false)
    private boolean anonymous = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
