package az.millers.hcm.performance.domain;

import java.time.LocalDate;
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

/** HCM_12 M399 — one typed development action (PRD §21.2). */
@Entity
@Table(name = "development_action", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class DevelopmentAction {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    /** §21.2 — TRAINING_COURSE … LEADERSHIP_PROGRAM. */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType = "TRAINING_COURSE";

    @Column(nullable = false, length = 1000)
    private String description;

    /** LMS course link (enrolment seam — §21.3). */
    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** PLANNED | IN_PROGRESS | DONE | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "PLANNED";

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
