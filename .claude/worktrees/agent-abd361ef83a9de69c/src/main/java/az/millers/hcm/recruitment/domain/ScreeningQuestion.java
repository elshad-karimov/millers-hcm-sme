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
 * M289 — Recruitment PRD §15: a knockout / pre-screening question on a
 * {@link JobPosting}. Candidates answer these at apply time; a failed
 * {@code knockout} question auto-rejects the application.
 */
@Entity
@Table(name = "screening_question", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class ScreeningQuestion {

    @Id
    private UUID id;

    @Column(name = "posting_id", nullable = false)
    private UUID postingId;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 16)
    private ScreeningQuestionType questionType;

    /** SINGLE_CHOICE: newline-separated choices shown to the candidate. */
    @Column(columnDefinition = "text")
    private String options;

    /** Failing this question auto-rejects the application. */
    @Column(nullable = false)
    private boolean knockout = true;

    @Column(nullable = false)
    private boolean required = true;

    /** BOOLEAN: the passing answer (null = any answer passes). */
    @Column(name = "expected_boolean")
    private Boolean expectedBoolean;

    /** SINGLE_CHOICE: newline-separated passing values (empty = any passes). */
    @Column(name = "acceptable_options", columnDefinition = "text")
    private String acceptableOptions;

    /** NUMERIC: inclusive lower bound (null = open). */
    @Column(name = "min_value")
    private BigDecimal minValue;

    /** NUMERIC: inclusive upper bound (null = open). */
    @Column(name = "max_value")
    private BigDecimal maxValue;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_by", length = 120)
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
