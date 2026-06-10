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

/**
 * M260 — Position transfer (PRD §40).
 *
 * <p>Moves a single position from one org unit / cost-centre / location
 * to another with workflow approval. Both sides are SNAPSHOTTED at
 * request time so the historical record survives later renames or
 * deletes on the source / destination.
 */
@Entity
@Table(name = "position_transfer", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class PositionTransfer {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    // ── FROM (snapshot at request time) ───────────────────────────────
    @Column(name = "from_org_unit_id")
    private UUID fromOrgUnitId;

    @Column(name = "from_org_unit_label", length = 200)
    private String fromOrgUnitLabel;

    @Column(name = "from_cost_centre", length = 64)
    private String fromCostCentre;

    @Column(name = "from_location", length = 160)
    private String fromLocation;

    // ── TO (target) ───────────────────────────────────────────────────
    @Column(name = "to_org_unit_id")
    private UUID toOrgUnitId;

    @Column(name = "to_org_unit_label", length = 200)
    private String toOrgUnitLabel;

    @Column(name = "to_cost_centre", length = 64)
    private String toCostCentre;

    @Column(name = "to_location", length = 160)
    private String toLocation;

    /** §22 reason master code; reason category VACANCY (closest fit). */
    @Column(name = "transfer_reason", length = 64)
    private String transferReason;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransferStatus status = TransferStatus.DRAFT;

    // ── Workflow breadcrumbs ──────────────────────────────────────────
    @Column(name = "requested_by", length = 120)
    private String requestedBy;

    @Column(name = "requested_at")
    private OffsetDateTime requestedAt;

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

    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;

    @Column(name = "completed_by", length = 120)
    private String completedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_by", length = 120)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
