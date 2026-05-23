package az.millers.hcm.recruitment.api.dto;

import az.millers.hcm.recruitment.domain.ApplicationStage;
import az.millers.hcm.recruitment.domain.Recommendation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StageTransitionRequest(
        @NotNull ApplicationStage toStage,
        @Min(1) @Max(5) Integer rating,
        Recommendation recommendation,
        String comment) {
}
