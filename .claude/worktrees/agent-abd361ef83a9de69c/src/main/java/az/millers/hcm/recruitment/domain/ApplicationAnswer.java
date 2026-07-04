package az.millers.hcm.recruitment.domain;

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

/**
 * M289 — Recruitment PRD §15: a candidate's answer to one
 * {@link ScreeningQuestion}, captured at apply time with the computed
 * pass/fail and a text snapshot of the question (so the recruiter view
 * survives later question edits / deletes).
 */
@Entity
@Table(name = "application_answer", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationAnswer {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "question_text_snapshot", nullable = false, columnDefinition = "text")
    private String questionTextSnapshot;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    @Column(nullable = false)
    private boolean passed;

    /** Snapshot of whether this was a knockout question at apply time. */
    @Column(nullable = false)
    private boolean knockout;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
