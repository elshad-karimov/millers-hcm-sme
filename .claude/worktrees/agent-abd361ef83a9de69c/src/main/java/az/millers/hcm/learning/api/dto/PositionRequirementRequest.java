package az.millers.hcm.learning.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PositionRequirementRequest(
        @NotNull UUID competencyId,
        @Min(1) @Max(5) int requiredProficiency,
        String notes
) {}
