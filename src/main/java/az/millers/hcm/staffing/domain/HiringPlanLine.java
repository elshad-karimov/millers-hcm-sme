package az.millers.hcm.staffing.domain;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M422: Hiring plan line — generated from workforce plan NEW_HIRE targets.
 */
@Entity
@Table(name = "hiring_plan_line", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class HiringPlanLine {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "workforce_plan_id", nullable = false)
    private UUID workforcePlanId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "target_start_date")
    private LocalDate targetStartDate;

    @Column(name = "recruiter_employee_id")
    private UUID recruiterEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recruitment_status", nullable = false, length = 20)
    private RecruitmentStatus recruitmentStatus = RecruitmentStatus.PLANNED;

    @Column(name = "vacancy_id")
    private UUID vacancyId;

    @Column(nullable = false)
    private int headcount = 1;

    @Column(columnDefinition = "text")
    private String notes;

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

    public enum RecruitmentStatus {
        PLANNED,
        VACANCY_OPEN,
        HIRED,
        CANCELLED
    }
}
