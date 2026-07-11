package az.millers.hcm.selfservice.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.EventType;

/**
 * M495 — Mobile attendance punch response.
 */
public record MobilePunchResponse(
        UUID eventId,
        EventType type,
        OffsetDateTime recordedAt,
        GeofenceStatus geofenceStatus,
        boolean flagged
) {
    public enum GeofenceStatus {
        INSIDE,
        OUTSIDE,
        UNKNOWN
    }
}
