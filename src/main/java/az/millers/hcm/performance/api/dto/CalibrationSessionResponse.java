package az.millers.hcm.performance.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.performance.domain.CalibrationSession;

public record CalibrationSessionResponse(
        UUID id,
        UUID cycleId,
        String name,
        OffsetDateTime scheduledAt,
        String status,
        String facilitator,
        String notes,
        OffsetDateTime createdAt) {

    public static CalibrationSessionResponse from(CalibrationSession s) {
        return new CalibrationSessionResponse(
                s.getId(),
                s.getCycleId(),
                s.getName(),
                s.getScheduledAt(),
                s.getStatus(),
                s.getFacilitator(),
                s.getNotes(),
                s.getCreatedAt());
    }
}
