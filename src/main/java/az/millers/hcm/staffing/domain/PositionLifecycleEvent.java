package az.millers.hcm.staffing.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only audit row for a single position lifecycle transition (M243).
 * One row per state change; the {@link Position}'s freeze/closure/approval
 * breadcrumb columns are a denormalised cache of the most recent event.
 */
@Entity
@Table(name = "position_lifecycle_event", schema = "staffing")
public class PositionLifecycleEvent {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    /** Nullable — the synthetic creation row has no "from" state. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private PositionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private PositionStatus toStatus;

    @Column(name = "reason")
    private String reason;

    @Column(name = "comments")
    private String comments;

    /** Set when freezing with a scheduled auto-unfreeze date. */
    @Column(name = "scheduled_unfreeze_date")
    private LocalDate scheduledUnfreezeDate;

    @Column(name = "actor")
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPositionId() { return positionId; }
    public void setPositionId(UUID positionId) { this.positionId = positionId; }
    public PositionStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(PositionStatus fromStatus) { this.fromStatus = fromStatus; }
    public PositionStatus getToStatus() { return toStatus; }
    public void setToStatus(PositionStatus toStatus) { this.toStatus = toStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public LocalDate getScheduledUnfreezeDate() { return scheduledUnfreezeDate; }
    public void setScheduledUnfreezeDate(LocalDate scheduledUnfreezeDate) { this.scheduledUnfreezeDate = scheduledUnfreezeDate; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
