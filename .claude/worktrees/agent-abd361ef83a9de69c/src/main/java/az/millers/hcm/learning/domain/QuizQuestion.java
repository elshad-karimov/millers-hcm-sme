package az.millers.hcm.learning.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quiz_question", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class QuizQuestion {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "question_no", nullable = false)
    private int questionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 16)
    private QuestionType questionType;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    /** Array of {@code {"key":"A","text":"..."}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices_json", nullable = false)
    private List<Map<String, Object>> choices;

    /** Array of correct choice keys (e.g. {@code ["A","C"]}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_keys", nullable = false)
    private List<String> correctKeys;

    @Column(columnDefinition = "text")
    private String explanation;

    @Column(nullable = false)
    private int points = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (questionType == null) questionType = QuestionType.MULTIPLE_CHOICE;
    }
}
