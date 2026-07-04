package az.millers.hcm.learning.domain;

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
 * A named, ordered sequence of courses (M46 — Learning Paths).
 *
 * <p>Employees are auto-enrolled in the next step of an active path when they
 * PASS a step whose {@code required_to_advance} flag is true.
 */
@Entity
@Table(name = "learning_path", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class LearningPath {

    @Id
    private UUID id;

    @Column(name = "path_no", nullable = false, unique = true)
    private String pathNo;

    @Column(nullable = false, length = 240)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

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
