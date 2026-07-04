package az.millers.hcm.staffing.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.staffing.domain.PositionLifecycleEvent;
import az.millers.hcm.staffing.domain.PositionStatus;
import jakarta.validation.constraints.Size;

/**
 * M243 — request / response payloads for the position lifecycle endpoints.
 *
 * <p>All transition endpoints accept the same request shape so the SPA can
 * use a single helper to call any of them. Unused fields per transition
 * (e.g. {@code scheduledUnfreezeDate} on a non-freeze action) are simply
 * ignored by the service.
 */
public final class PositionLifecycleDtos {

    private PositionLifecycleDtos() {}

    /** Generic body for every lifecycle transition. */
    public record LifecycleActionRequest(
            @Size(max = 1000) String reason,
            @Size(max = 2000) String comments,
            LocalDate scheduledUnfreezeDate) {}

    /** One row of the lifecycle history list. */
    public record LifecycleEventResponse(
            UUID id,
            UUID positionId,
            PositionStatus fromStatus,
            PositionStatus toStatus,
            String reason,
            String comments,
            LocalDate scheduledUnfreezeDate,
            String actor,
            OffsetDateTime occurredAt) {

        public static LifecycleEventResponse from(PositionLifecycleEvent e) {
            return new LifecycleEventResponse(
                    e.getId(),
                    e.getPositionId(),
                    e.getFromStatus(),
                    e.getToStatus(),
                    e.getReason(),
                    e.getComments(),
                    e.getScheduledUnfreezeDate(),
                    e.getActor(),
                    e.getOccurredAt());
        }
    }
}
