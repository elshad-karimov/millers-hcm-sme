package az.millers.hcm.performance.api.dto;

import java.time.LocalDate;
import java.util.Map;

import az.millers.hcm.performance.domain.CycleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewCycleRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull CycleType cycleType,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        LocalDate selfReviewDue,
        LocalDate managerReviewDue,
        LocalDate finalDue,
        String description,
        Map<String, Object> ratingScale) {
}
