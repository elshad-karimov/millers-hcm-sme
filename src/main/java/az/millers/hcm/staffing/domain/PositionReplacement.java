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

/** M246 / PRD §16 — replacement workflow record for a leaving occupant. */
@Entity
@Table(name = "position_replacement", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class PositionReplacement {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "leaving_employee_id", nullable = false)
    private UUID leavingEmployeeId;

    @Column(name = "leaving_occupancy_id")
    private UUID leavingOccupancyId;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "last_working_day", nullable = false)
    private LocalDate lastWorkingDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private ReplacementAction action = ReplacementAction.OPEN_RECRUITMENT;

    @Column(name = "replacement_employee_id")
    private UUID replacementEmployeeId;

    @Column(name = "replacement_start_date")
    private LocalDate replacementStartDate;

    @Column(name = "handover_overlap_days")
    private Integer handoverOverlapDays = 0;

    @Column(name = "vacancy_id")
    private UUID vacancyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReplacementStatus status = ReplacementStatus.DRAFT;

    @Column(name = "submitted_by", length = 120)
    private String submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_by", length = 120)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = ReplacementStatus.DRAFT;
        if (action == null) action = ReplacementAction.OPEN_RECRUITMENT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
