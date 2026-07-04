package az.millers.hcm.performance.domain;

import java.time.LocalDate;
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
 * HCM_16 M413 — succession plan (PRD §16.3/§16.4). Lifecycle:
 * DRAFT → (submit) → SUBMITTED → (workflow APPROVED) → APPROVED →
 * (manual activate) → ACTIVE / ARCHIVED. Workflow subject entity = 'SuccessionPlan'.
 */
@Entity
@Table(name = "succession_plan", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class SuccessionPlan {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "critical_position_id", nullable = false)
    private UUID criticalPositionId;

    @Column(name = "plan_owner_employee_id", nullable = false)
    private UUID planOwnerEmployeeId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    /** DRAFT | SUBMITTED | APPROVED | ACTIVE | ARCHIVED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "emergency_successor_employee_id")
    private UUID emergencySuccessorEmployeeId;

    @Column(length = 2000)
    private String notes;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
