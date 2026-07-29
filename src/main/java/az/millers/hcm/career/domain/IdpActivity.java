package az.millers.hcm.career.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(schema = "learning", name = "idp_activity")
@Getter @Setter
public class IdpActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idp_id", nullable = false)
    private Idp idp;

    @Column(nullable = false)
    private String title;

    @Column(name = "activity_type", nullable = false)
    private String activityType = "COURSE";  // COURSE|MENTORING|PROJECT|CONFERENCE|READING|OTHER

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private String status = "PENDING";  // PENDING|IN_PROGRESS|DONE|SKIPPED

    @Column(name = "completed_at")
    private LocalDate completedAt;

    @Column
    private String notes;
}
