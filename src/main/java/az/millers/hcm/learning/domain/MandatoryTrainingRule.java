package az.millers.hcm.learning.domain;

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
import org.hibernate.annotations.TenantId;

/** HCM_14 M406 — compliance-training rule: audience scope + recurrence (PRD 14 §9/§16). */
@Entity
@Table(name = "mandatory_training_rule", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class MandatoryTrainingRule {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false, length = 200)
    private String name;

    /** Audience filters — all null = every active employee. */
    @Column(name = "department_name", length = 200)
    private String departmentName;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "work_location_id")
    private UUID workLocationId;

    /** Null = one-time mandatory; N = must re-complete every N months. */
    @Column(name = "recurrence_months")
    private Integer recurrenceMonths;

    /** Days to complete after (re-)assignment. */
    @Column(name = "due_days", nullable = false)
    private int dueDays = 30;

    @Column(name = "reminder_days_before", nullable = false)
    private int reminderDaysBefore = 7;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
