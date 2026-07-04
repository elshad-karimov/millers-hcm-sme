package az.millers.hcm.performance.api.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CalibrationSessionRequest(
        @NotBlank @Size(max = 160) String name,
        OffsetDateTime scheduledAt,
        @Size(max = 120) String facilitator,
        String notes) {
}
