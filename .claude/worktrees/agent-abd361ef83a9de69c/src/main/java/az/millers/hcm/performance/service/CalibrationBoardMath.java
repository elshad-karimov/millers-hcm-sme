package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import az.millers.hcm.common.BadRequestException;

/**
 * M121 — pure-static math for the calibration board distribution view.
 *
 * <p>Mockito-free (Java 25 class-file v69 isn't supported by Byte
 * Buddy), so the contract gets pinned by plain JUnit + AssertJ tests.
 *
 * <p>The five canonical bands are defined here as a single constant —
 * {@link az.millers.hcm.performance.service.CalibrationSessionService}'s
 * existing distribution code uses the same labels, so band keys agree
 * across actual + target.
 */
public final class CalibrationBoardMath {

    /** Ordered display labels — high to low. */
    public static final List<String> CANONICAL_BANDS = List.of(
            "5 - Exceptional",
            "4 - Exceeds",
            "3 - Meets",
            "2 - Needs Improvement",
            "1 - Unsatisfactory");

    /** The bucket label for reviews with no rating yet. */
    public static final String UNRATED = "Unrated";

    private CalibrationBoardMath() {}

    /**
     * Validate a HR-supplied target map. The sum must not exceed 100%
     * (we allow under-100 — an HR admin might intentionally leave
     * "Unrated" as the residual). Every band must be one of
     * {@link #CANONICAL_BANDS}. Percentages must be ≥ 0.
     *
     * <p>Throws {@link BadRequestException} on the first problem.
     */
    public static void validateTargets(Map<String, BigDecimal> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new BadRequestException("At least one band target is required");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : targets.entrySet()) {
            String band = e.getKey();
            BigDecimal pct = e.getValue();
            if (!CANONICAL_BANDS.contains(band)) {
                throw new BadRequestException("Unknown band: " + band);
            }
            if (pct == null) {
                throw new BadRequestException("Target percent is required for " + band);
            }
            if (pct.signum() < 0) {
                throw new BadRequestException("Target percent must be ≥ 0 for " + band);
            }
            if (pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BadRequestException("Target percent cannot exceed 100 for " + band);
            }
            sum = sum.add(pct);
        }
        if (sum.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException(
                    "Target percentages sum to " + sum.setScale(2, RoundingMode.HALF_UP)
                            + "% — must not exceed 100%");
        }
    }

    /**
     * Build a band → BoardCell map (actual count + actual % + target %
     * + delta) keyed in display order. Bands the targets don't mention
     * still appear if actuals contribute to them.
     */
    public static Map<String, BoardCell> buildBoardDistribution(
            Map<String, Long> actuals,
            Map<String, BigDecimal> targets,
            long totalReviews) {
        Map<String, BoardCell> out = new LinkedHashMap<>();
        if (totalReviews <= 0) {
            // Still emit one row per known band so the UI knows which
            // bands exist.
            for (String band : CANONICAL_BANDS) {
                BigDecimal target = targets == null ? null : targets.get(band);
                out.put(band, new BoardCell(0L, BigDecimal.ZERO, target, target == null ? null : target.negate()));
            }
            return out;
        }
        BigDecimal total = BigDecimal.valueOf(totalReviews);
        // Canonical bands first so the chart axis order is stable.
        for (String band : CANONICAL_BANDS) {
            long count = actuals == null ? 0 : actuals.getOrDefault(band, 0L);
            BigDecimal actualPct = BigDecimal.valueOf(count)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP);
            BigDecimal target = targets == null ? null : targets.get(band);
            BigDecimal delta = target == null ? null : actualPct.subtract(target);
            out.put(band, new BoardCell(count, actualPct, target, delta));
        }
        // Anything else (e.g. "Unrated", or a legacy custom band) goes
        // after the canonical five.
        if (actuals != null) {
            for (Map.Entry<String, Long> e : actuals.entrySet()) {
                if (out.containsKey(e.getKey())) continue;
                long count = e.getValue();
                BigDecimal actualPct = BigDecimal.valueOf(count)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP);
                BigDecimal target = targets == null ? null : targets.get(e.getKey());
                BigDecimal delta = target == null ? null : actualPct.subtract(target);
                out.put(e.getKey(), new BoardCell(count, actualPct, target, delta));
            }
        }
        return out;
    }

    /**
     * One row of the board distribution.
     *
     * @param actualCount   number of reviews in this band
     * @param actualPercent {@code actualCount / total × 100}, 2 dp
     * @param targetPercent the configured target (null if HR didn't set one)
     * @param delta         {@code actualPercent − targetPercent}, null if target null
     */
    public record BoardCell(
            long actualCount,
            BigDecimal actualPercent,
            BigDecimal targetPercent,
            BigDecimal delta) {}
}
