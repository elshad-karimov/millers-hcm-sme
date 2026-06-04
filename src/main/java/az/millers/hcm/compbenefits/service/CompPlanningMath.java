package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure-math helpers for the M118 compensation planning workbench.
 *
 * <p>Three calculations that need pinning:
 * <ul>
 *   <li>{@link #increasePercent(BigDecimal, BigDecimal)} — converts the
 *       proposed-vs-current pair into the percentage the dashboard
 *       displays. Off-by-one (or wrong base) here lets every increase
 *       look bigger or smaller than it really is.</li>
 *   <li>{@link #applyPercent(BigDecimal, BigDecimal)} — the inverse:
 *       given a base and a percentage, what's the new salary? Used when
 *       the UI lets a manager type "+5%" instead of an absolute number.</li>
 *   <li>{@link #remaining(BigDecimal, BigDecimal)} — pool minus committed,
 *       never below zero. Negative would let proposals reach into the
 *       red without warning the HR reviewer.</li>
 * </ul>
 */
public final class CompPlanningMath {

    private CompPlanningMath() {}

    /**
     * Percentage difference between {@code current} and {@code proposed},
     * rounded to one decimal. Returns {@code null} for missing or zero
     * base — division-by-zero must not silently emit infinity.
     */
    public static Double increasePercent(BigDecimal current, BigDecimal proposed) {
        if (current == null || proposed == null) return null;
        if (current.signum() <= 0) return null;
        BigDecimal delta = proposed.subtract(current);
        BigDecimal pct = delta
                .multiply(BigDecimal.valueOf(100))
                .divide(current, 1, RoundingMode.HALF_UP);
        return pct.doubleValue();
    }

    /**
     * Apply a percentage to a base salary, rounded to two decimal places
     * (the precision of the DB column).
     */
    public static BigDecimal applyPercent(BigDecimal base, BigDecimal percent) {
        if (base == null || percent == null) return null;
        return base
                .multiply(BigDecimal.ONE.add(percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Budget pool remaining. Never returns negative — the UI surfaces
     * "over budget" by checking {@code committed > total} separately so
     * the meter math stays a non-negative percentage.
     */
    public static BigDecimal remaining(BigDecimal poolTotal, BigDecimal committed) {
        if (poolTotal == null) return BigDecimal.ZERO;
        BigDecimal safe = committed == null ? BigDecimal.ZERO : committed;
        BigDecimal diff = poolTotal.subtract(safe);
        return diff.signum() < 0 ? BigDecimal.ZERO : diff;
    }

    /** True iff committing this delta would push the pool past {@code poolTotal}. */
    public static boolean wouldExceedPool(BigDecimal poolTotal,
                                            BigDecimal currentlyCommitted,
                                            BigDecimal delta) {
        if (poolTotal == null) return false;
        BigDecimal committed = currentlyCommitted == null ? BigDecimal.ZERO : currentlyCommitted;
        BigDecimal d = delta == null ? BigDecimal.ZERO : delta;
        return committed.add(d).compareTo(poolTotal) > 0;
    }
}
