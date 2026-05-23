package az.millers.hcm.learning.domain;

import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quiz_answer", schema = "learning",
        uniqueConstraints = @UniqueConstraint(columnNames = {"attempt_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
public class QuizAnswer {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_keys", nullable = false)
    private List<String> selectedKeys;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "points_awarded", nullable = false)
    private int pointsAwarded;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
