package az.millers.hcm.learning.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LearningPathCourseRequest(
        @NotNull UUID courseId,
        @Min(1) int stepOrder,
        boolean requiredToAdvance) {
}
