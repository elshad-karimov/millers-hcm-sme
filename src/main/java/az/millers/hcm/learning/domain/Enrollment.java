package az.millers.hcm.learning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "enrollment", schema = "learning",
        uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "employee_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Enrollment {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "enrollment_no", nullable = false, unique = true)
    private String enrollmentNo;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnrollmentStatus status;

    @Column(name = "enrolled_at", nullable = false)
    private OffsetDateTime enrolledAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "attempts_used", nullable = false)
    private int attemptsUsed;

    @Column(name = "best_score_percent", precision = 5, scale = 2)
    private BigDecimal bestScorePercent;

    @Column(name = "last_score_percent", precision = 5, scale = 2)
    private BigDecimal lastScorePercent;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrolled_via", nullable = false, length = 16)
    private EnrolledVia enrolledVia;

    @Column(name = "assigned_by")
    private String assignedBy;

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
        if (enrolledAt == null) enrolledAt = now;
        if (status == null) status = EnrollmentStatus.ENROLLED;
        if (enrolledVia == null) enrolledVia = EnrolledVia.SELF_ENROLLED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
