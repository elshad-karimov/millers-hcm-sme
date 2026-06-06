package az.millers.hcm.workflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * M126 — pure-static SLA math for the workflow breach detector. Kept
 * Mockito-free so the contract gets pinned by plain JUnit (Java 25
 * class-file v69 isn't supported by Byte Buddy).
 *
 * <p>The math itself is intentionally trivial — most of the value is
 * codifying the "treat null/zero/negative SLA as no-SLA" and "null
 * stepEnteredAt means unknown — never breached" rules so callers don't
 * each reinvent them.
 */
public final class SlaCalculator {

    private SlaCalculator() {}

    /**
     * Wall-clock when the SLA on a step entered at {@code stepEnteredAt}
     * with budget {@code slaHours} runs out. Returns {@code null} if
     * either input is null or the SLA isn't a positive budget.
     */
    public static OffsetDateTime dueAt(OffsetDateTime stepEnteredAt, Integer slaHours) {
        if (stepEnteredAt == null || slaHours == null || slaHours <= 0) return null;
        return stepEnteredAt.plusHours(slaHours);
    }

    /**
     * True iff {@code now} is strictly after the computed
     * {@code dueAt}. A null/zero/negative SLA never breaches — that's
     * how callers opt a step out of SLA enforcement.
     */
    public static boolean isBreached(OffsetDateTime stepEnteredAt,
                                     Integer slaHours,
                                     OffsetDateTime now) {
        OffsetDateTime due = dueAt(stepEnteredAt, slaHours);
        return due != null && now != null && now.isAfter(due);
    }

    /**
     * Hours elapsed past the SLA deadline, rounded to 2 dp. Returns
     * {@code 0} when not breached so callers can use it as a numeric
     * severity directly. Negative budgets / nulls → 0.
     */
    public static BigDecimal hoursOverdue(OffsetDateTime stepEnteredAt,
                                          Integer slaHours,
                                          OffsetDateTime now) {
        OffsetDateTime due = dueAt(stepEnteredAt, slaHours);
        if (due == null || now == null || !now.isAfter(due)) return BigDecimal.ZERO;
        long minutes = Duration.between(due, now).toMinutes();
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /**
     * Percent of SLA budget consumed (0..100+). Useful for an SPA
     * progress bar that turns red past 100. Returns 0 when there's no
     * SLA budget so callers can render "no SLA" rather than a divide-by-zero.
     */
    public static BigDecimal percentConsumed(OffsetDateTime stepEnteredAt,
                                              Integer slaHours,
                                              OffsetDateTime now) {
        if (stepEnteredAt == null || slaHours == null || slaHours <= 0 || now == null) {
            return BigDecimal.ZERO;
        }
        long elapsedMin = Duration.between(stepEnteredAt, now).toMinutes();
        if (elapsedMin <= 0) return BigDecimal.ZERO;
        BigDecimal budgetMin = BigDecimal.valueOf(slaHours).multiply(BigDecimal.valueOf(60));
        return BigDecimal.valueOf(elapsedMin)
                .multiply(BigDecimal.valueOf(100))
                .divide(budgetMin, 1, RoundingMode.HALF_UP);
    }
}
