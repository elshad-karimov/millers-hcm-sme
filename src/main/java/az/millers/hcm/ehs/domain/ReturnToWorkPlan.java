package az.millers.hcm.ehs.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "return_to_work_plan", schema = "ehs")
@Data
public class ReturnToWorkPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "injury_report_id")
    private UUID injuryReportId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "medical_clearance_date")
    private LocalDate medicalClearanceDate;

    @Column(name = "restrictions", length = 2000)
    private String restrictions;

    @Column(name = "modified_schedule", length = 1000)
    private String modifiedSchedule;

    @Column(name = "manager_approved", nullable = false)
    private Boolean managerApproved = false;

    @Column(name = "hr_approved", nullable = false)
    private Boolean hrApproved = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReturnToWorkStatus status = ReturnToWorkStatus.DRAFT;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
