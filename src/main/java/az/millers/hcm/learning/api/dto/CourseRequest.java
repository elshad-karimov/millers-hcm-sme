package az.millers.hcm.learning.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import az.millers.hcm.learning.domain.CourseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CourseRequest(
        @NotBlank String code,
        @NotBlank String title,
        String description,
        String contentMarkdown,
        @NotNull CourseCategory category,
        BigDecimal durationHours,
        boolean mandatory,
        Integer passingScore,
        Integer maxAttempts,
        UUID instructorId,
        Integer validForMonths,
        String coverUrl) {
}
