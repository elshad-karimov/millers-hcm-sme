package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * HCM_12 M391 — pure OKR progress math (PRD §8.2). Package-private static helpers,
 * same pattern as {@link KpiScoringMath}.
 *
 * <ul>
 *   <li>KR progress = (current − baseline) / (target − baseline) × 100, clamped 0..100.
 *       BOOLEAN measurement → 0 or 100 (current ≥ target). Degenerate target == baseline
 *       → 100 once current reaches target, else 0.</li>
 *   <li>Objective progress = Σ(KR progress × weight) / Σ weight; when every weight is 0,
 *       a simple average. CANCELLED KRs are excluded by the caller.</li>
 * </ul>
 */
final class OkrMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private OkrMath() {}

    static BigDecimal krProgress(String measurementType, BigDecimal baseline,
                                 BigDecimal target, BigDecimal current) {
        if (current == null || target == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = baseline == null ? BigDecimal.ZERO : baseline;
        if ("BOOLEAN".equals(measurementType)) {
            return (current.compareTo(target) >= 0 ? HUNDRED : BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal span = target.subtract(base);
        if (span.signum() == 0) {
            return (current.compareTo(target) >= 0 ? HUNDRED : BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal raw = current.subtract(base)
                .divide(span, 6, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
        if (raw.compareTo(BigDecimal.ZERO) < 0) raw = BigDecimal.ZERO;
        if (raw.compareTo(HUNDRED) > 0) raw = HUNDRED;
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    /** items = (progressPercent, weightPercent) pairs of the objective's live KRs. */
    static BigDecimal objectiveProgress(List<BigDecimal[]> items) {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal weightSum = BigDecimal.ZERO;
        for (BigDecimal[] it : items) {
            if (it[1] != null) weightSum = weightSum.add(it[1]);
        }
        BigDecimal total = BigDecimal.ZERO;
        if (weightSum.signum() > 0) {
            for (BigDecimal[] it : items) {
                BigDecimal p = it[0] == null ? BigDecimal.ZERO : it[0];
                BigDecimal w = it[1] == null ? BigDecimal.ZERO : it[1];
                total = total.add(p.multiply(w));
            }
            return total.divide(weightSum, 2, RoundingMode.HALF_UP);
        }
        for (BigDecimal[] it : items) {
            total = total.add(it[0] == null ? BigDecimal.ZERO : it[0]);
        }
        return total.divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    }
}
