package az.millers.hcm.staffing.domain;

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
import org.hibernate.annotations.TenantId;

/**
 * A manager's request to change the approved headcount on a position (M156 / §8.3.7).
 *
 * <p>The request is routed through the workflow engine.  On APPROVED the service
 * increments the position's {@code approvedHeadcount} and opens the corresponding
 * number of VACANT vacancy records so Recruitment can fill them.
 */
@Entity
@Table(name = "headcount_change_request", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class HeadcountChangeRequest {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    /** Positive = increase, negative = decrease. Never zero (DB-constrained). */
    @Column(name = "requested_delta", nullable = false)
    private int requestedDelta;

    @Column
    private String reason;

    /** PENDING | APPROVED | REJECTED | WITHDRAWN */
    @Column(nullable = false)
    private String status;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "requested_at")
    private OffsetDateTime requestedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = "PENDING";
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (requestedAt == null) requestedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
