package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.performance.domain.Goal;
import az.millers.hcm.performance.domain.GoalCategory;
import az.millers.hcm.performance.domain.GoalStatus;

public record GoalResponse(
        UUID id,
        String goalNo,
        UUID cycleId,
        UUID employeeId,
        UUID parentGoalId,
        String title,
        String description,
        GoalCategory category,
        String targetMetric,
        BigDecimal weightPercent,
        BigDecimal progressPercent,
        GoalStatus status,
        LocalDate dueDate,
        BigDecimal rating,
        String ratingNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy) {

    public static GoalResponse from(Goal g) {
        return new GoalResponse(
                g.getId(), g.getGoalNo(), g.getCycleId(), g.getEmployeeId(), g.getParentGoalId(),
                g.getTitle(), g.getDescription(), g.getCategory(), g.getTargetMetric(),
                g.getWeightPercent(), g.getProgressPercent(), g.getStatus(), g.getDueDate(),
                g.getRating(), g.getRatingNote(),
                g.getCreatedAt(), g.getUpdatedAt(), g.getCreatedBy(), g.getUpdatedBy());
    }
}
