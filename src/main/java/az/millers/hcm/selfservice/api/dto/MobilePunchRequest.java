package az.millers.hcm.selfservice.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.EventType;
import jakarta.validation.constraints.NotNull;

/**
 * M495 — Mobile attendance punch request.
 */
public record MobilePunchRequest(
        @NotNull EventType type,
        @NotNull OffsetDateTime timestamp,
        Double latitude,
        Double longitude,
        Double gpsAccuracy,
        String deviceId,
        UUID selfieAttachmentId,
        String offlineQueueId
) {
}
