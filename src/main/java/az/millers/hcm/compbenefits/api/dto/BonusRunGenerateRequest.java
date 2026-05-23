package az.millers.hcm.compbenefits.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BonusRunGenerateRequest(
        @NotBlank String name,
        @NotNull UUID cycleId,
        @NotNull Integer periodYear,
        @NotNull @Min(1) @Max(12) Integer periodMonth,
        String currency,
        String note) {
}
