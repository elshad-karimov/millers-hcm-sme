package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.performance.domain.GoalCategory;
import az.millers.hcm.performance.domain.GoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GoalRequest(
        @NotNull UUID cycleId,
        @NotNull UUID employeeId,
        UUID parentGoalId,
        @NotBlank String title,
        String description,
        @NotNull GoalCategory category,
        String targetMetric,
        BigDecimal weightPercent,
        BigDecimal progressPercent,
        GoalStatus status,
        LocalDate dueDate) {
}
