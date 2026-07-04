package az.millers.hcm.performance.event;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.performance.domain.CycleType;

/**
 * Published by {@link az.millers.hcm.performance.service.ReviewCycleService}
 * when a review cycle transitions to {@code CycleStatus.COMPLETED} (M184 /
 * PRD §8.13 AC: "writes the bonus line into the next payroll run").
 *
 * @param cycleId     the completed cycle
 * @param cycleCode   human-readable code (e.g. ANNUAL-2025)
 * @param cycleName   display name
 * @param cycleType   ANNUAL / MID_YEAR / QUARTERLY etc.
 * @param periodEnd   end of the performance period (bonus is paid in the
 *                    month after this date)
 */
public record ReviewCycleCompletedEvent(
        UUID cycleId,
        String cycleCode,
        String cycleName,
        CycleType cycleType,
        LocalDate periodEnd) {}
