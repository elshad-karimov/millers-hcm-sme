package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * HCM_12 M390 — pure KPI scoring math (PRD §7.4). Package-private static helpers so a
 * unit test can pin the numbers without Spring.
 *
 * <ul>
 *   <li>achievement% = actual / target × 100 (target must be &gt; 0)</li>
 *   <li>LINEAR    → rating = achievement / 20, clamped to [1, 5]
 *       (proportional: 100% of target = 5.0, 60% = 3.0)</li>
 *   <li>THRESHOLD → banded: ≥110→5, ≥100→4, ≥80→3, ≥60→2, else 1
 *       (hit target = 4 "Exceeds"; beat it by 10% = 5)</li>
 * </ul>
 */
final class KpiScoringMath {

    private KpiScoringMath() {}

    static BigDecimal achievementPercent(BigDecimal actual, BigDecimal target) {
        if (actual == null || target == null || target.signum() <= 0) return null;
        return actual.divide(target, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal rating(String scoringModel, BigDecimal achievementPercent) {
        if (achievementPercent == null) return null;
        if ("THRESHOLD".equals(scoringModel)) {
            if (achievementPercent.compareTo(BigDecimal.valueOf(110)) >= 0) return five(5);
            if (achievementPercent.compareTo(BigDecimal.valueOf(100)) >= 0) return five(4);
            if (achievementPercent.compareTo(BigDecimal.valueOf(80)) >= 0) return five(3);
            if (achievementPercent.compareTo(BigDecimal.valueOf(60)) >= 0) return five(2);
            return five(1);
        }
        // LINEAR: achievement / 20 → 1..5 clamped
        BigDecimal raw = achievementPercent.divide(BigDecimal.valueOf(20), 2, RoundingMode.HALF_UP);
        if (raw.compareTo(BigDecimal.ONE) < 0) return five(1);
        if (raw.compareTo(BigDecimal.valueOf(5)) > 0) return five(5);
        return raw;
    }

    private static BigDecimal five(int v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
