package az.millers.hcm.lifecycle.domain;

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

/**
 * A provisioning request spawned by an onboarding checklist task (M301 — Phase
 * B.1). Equipment / workspace tasks auto-create one of these when the checklist
 * starts; IT / Facilities track it to fulfilment. Fulfilling an
 * {@link ResourceRequestCategory#EQUIPMENT} request bridges to the M72/M124
 * asset module (creates an {@code EmployeeAsset} and links {@link #assetId}).
 */
@Entity
@Table(name = "onboarding_resource_request", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class OnboardingResourceRequest {

    @Id
    private UUID id;

    @Column(name = "request_no", nullable = false, unique = true, length = 20)
    private String requestNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    /** Originating checklist task; UNIQUE so a restart can't double-create. */
    @Column(name = "task_status_id")
    private UUID taskStatusId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceRequestCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceRequestStatus status = ResourceRequestStatus.REQUESTED;

    /** Linked {@code core_hr.employee_asset} once an EQUIPMENT request is fulfilled. */
    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (requestedAt == null) requestedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
