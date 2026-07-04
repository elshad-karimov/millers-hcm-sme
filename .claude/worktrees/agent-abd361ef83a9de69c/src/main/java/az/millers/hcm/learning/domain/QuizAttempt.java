package az.millers.hcm.learning.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quiz_attempt", schema = "learning",
        uniqueConstraints = @UniqueConstraint(columnNames = {"enrollment_id", "attempt_no"}))
@Getter
@Setter
@NoArgsConstructor
public class QuizAttempt {

    @Id
    private UUID id;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AttemptStatus status;

    @Column(name = "total_points")
    private Integer totalPoints;

    /** Sum of per-answer pointsAwarded; NUMERIC(7,2) to support MULTI_SELECT partial credit. */
    @Column(name = "earned_points", precision = 7, scale = 2)
    private BigDecimal earnedPoints;

    @Column(name = "score_percent", precision = 5, scale = 2)
    private BigDecimal scorePercent;

    @Column(name = "passing_score")
    private Integer passingScore;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
        if (status == null) status = AttemptStatus.IN_PROGRESS;
    }
}
