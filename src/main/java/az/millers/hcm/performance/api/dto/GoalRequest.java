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
        LocalDate dueDate,
        /** Optional: links this DEVELOPMENT goal to a course for auto-rating (M49). */
        UUID sourceCourseId,
        /** M403 — business goal type (PRD 13 §4) + org anchors for org-level goals. */
        UUID goalTypeId,
        UUID orgUnitId,
        UUID legalEntityId) {
}
