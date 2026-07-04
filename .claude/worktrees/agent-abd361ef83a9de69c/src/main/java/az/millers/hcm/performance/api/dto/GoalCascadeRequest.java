package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * M130 — body of {@code POST /api/performance/goals/{id}/cascade}.
 *
 * <p>HR / a manager copies an upstream goal onto a direct-report's
 * roster. Title + description + category + due-date all clone from the
 * parent. Weight defaults to the parent's; pass {@code weightPercent}
 * to override (a common adjustment — junior reports may only carry
 * part of the parent goal's load).
 *
 * <p>The cascaded child stays in the parent's cycle. Cross-cycle
 * cascade is intentionally rejected at the service layer — OKRs cascade
 * within one performance period.
 */
public record GoalCascadeRequest(
        @NotNull UUID employeeId,
        BigDecimal weightPercent) {
}
