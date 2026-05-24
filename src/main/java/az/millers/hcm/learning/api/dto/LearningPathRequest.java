package az.millers.hcm.learning.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LearningPathRequest(
        @NotBlank String name,
        String description,
        boolean active) {
}
