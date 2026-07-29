package az.millers.hcm.engagement.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * One answer inside a {@link SurveyResponse} (M116). Exactly one of
 * {@link #ratingValue}, {@link #textValue}, {@link #choiceValue} is
 * populated; the {@link SurveyQuestion#getQuestionType question type}
 * picks which.
 */
@Entity
@Table(name = "survey_answer", schema = "engagement")
@Getter
@Setter
@NoArgsConstructor
public class SurveyAnswer {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    /** RATING_1_5 (1..5), RATING_1_10 (0..10), BOOLEAN (0/1). */
    @Column(name = "rating_value")
    private Integer ratingValue;

    /** TEXT. */
    @Column(name = "text_value", columnDefinition = "text")
    private String textValue;

    /** MULTIPLE_CHOICE — chosen option string. */
    @Column(name = "choice_value", length = 200)
    private String choiceValue;

    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }
}
