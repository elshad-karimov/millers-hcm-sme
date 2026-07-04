package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;

import az.millers.hcm.performance.domain.GoalStatus;
import jakarta.validation.constraints.NotNull;

public record GoalProgressRequest(
        @NotNull BigDecimal progressPercent,
        GoalStatus status,
        String note) {
}
