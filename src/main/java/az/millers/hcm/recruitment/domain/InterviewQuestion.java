package az.millers.hcm.recruitment.domain;

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

/**
 * One weighted question inside an {@link InterviewKit} (M85). Score is
 * 1-5 (DB CHECK on {@code interview_score.score}); the kit's overall
 * score is the weight-normalised average.
 */
@Entity
@Table(name = "interview_question", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class InterviewQuestion {

    @Id
    private UUID id;

    @Column(name = "kit_id", nullable = false)
    private UUID kitId;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(nullable = false)
    private int weight = 1;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean required = true;

    @Column(nullable = false)
    private boolean active = true;

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
