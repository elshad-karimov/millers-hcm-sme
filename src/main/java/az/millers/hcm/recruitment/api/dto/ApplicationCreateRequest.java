package az.millers.hcm.recruitment.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ApplicationCreateRequest(
        @NotNull UUID vacancyId,
        @NotNull UUID candidateId) {
}
