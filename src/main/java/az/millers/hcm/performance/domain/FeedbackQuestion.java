package az.millers.hcm.performance.domain;

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

/** HCM_12 M395 — one questionnaire question (PRD §13.3). */
@Entity
@Table(name = "feedback_question", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class FeedbackQuestion {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "questionnaire_id", nullable = false)
    private UUID questionnaireId;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question_text", nullable = false, length = 500)
    private String questionText;

    /** RATING (1..5) | TEXT */
    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType = "RATING";

    /** COMPETENCY | BEHAVIORAL | COLLABORATION | LEADERSHIP | STRENGTHS | IMPROVEMENT | OPEN */
    @Column(length = 30)
    private String category;

    @Column(nullable = false)
    private boolean required = true;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
