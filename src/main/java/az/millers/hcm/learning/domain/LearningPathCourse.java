package az.millers.hcm.learning.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One step in a {@link LearningPath} — the pairing of a course with its
 * 1-based position ({@code step_order}) in the path.
 *
 * <p>When {@code requiredToAdvance} is {@code true} the path engine
 * auto-enrolls the employee in the next step after they PASS this one.
 */
@Entity
@Table(name = "learning_path_course", schema = "learning",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"path_id", "step_order"}),
            @UniqueConstraint(columnNames = {"path_id", "course_id"})
        })
@Getter
@Setter
@NoArgsConstructor
public class LearningPathCourse {

    @Id
    private UUID id;

    @Column(name = "path_id", nullable = false)
    private UUID pathId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** 1-based position within the path. Gaps allowed for easy reordering. */
    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    /** When true, PASS is required before the path engine advances to the next step. */
    @Column(name = "required_to_advance", nullable = false)
    private boolean requiredToAdvance = true;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
