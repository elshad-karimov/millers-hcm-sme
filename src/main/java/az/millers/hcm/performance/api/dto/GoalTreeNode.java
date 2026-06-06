package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * M130 — flat node in the goal tree. The SPA composes children by
 * matching {@code parentGoalId} → {@code id}; the {@code depth} and
 * {@code descendantCount} fields save the SPA from re-running the
 * tree math.
 *
 * @param alignmentPercent weighted-average progress across this goal's
 *     descendants (or own progress for a leaf). See
 *     {@link az.millers.hcm.performance.service.GoalTreeMath#alignmentPercent}.
 */
public record GoalTreeNode(
        UUID id,
        UUID parentGoalId,
        UUID employeeId,
        String employeeName,
        String goalNo,
        String title,
        BigDecimal weightPercent,
        BigDecimal progressPercent,
        int depth,
        int descendantCount,
        BigDecimal alignmentPercent) {
}
