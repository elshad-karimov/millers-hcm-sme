package az.millers.hcm.compbenefits.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Pure-math pinning for the M118 comp-planning calculators.
 *
 * <p>Every cell on the planning grid uses these helpers: the percentage in
 * each row, the budget meter at the top, and the over-budget warning. A
 * wrong base or wrong rounding leaks straight into manager-facing numbers
 * and into the audit trail.
 */
class CompPlanningMathTest {

    // ── increasePercent() ───────────────────────────────────────────────

    @Test
    void increaseFivePercentExactly() {
        // 1000 → 1050 should display as 5.0%.
        assertThat(CompPlanningMath.increasePercent(bd("1000"), bd("1050")))
                .isEqualTo(5.0);
    }

    @Test
    void increasePercentRoundsHalfUpToOneDecimal() {
        // 1000 → 1037 = 3.7% exactly. 1000 → 1037.5 = 3.75 → 3.8.
        assertThat(CompPlanningMath.increasePercent(bd("1000"), bd("1037")))
                .isEqualTo(3.7);
        assertThat(CompPlanningMath.increasePercent(bd("1000"), bd("1037.50")))
                .isEqualTo(3.8);
    }

    @Test
    void increasePercentZeroForSameSalary() {
        assertThat(CompPlanningMath.increasePercent(bd("2500"), bd("2500"))).isZero();
    }

    @Test
    void increasePercentNegativeForCut() {
        assertThat(CompPlanningMath.increasePercent(bd("1000"), bd("900"))).isEqualTo(-10.0);
    }

    @Test
    void increasePercentNullForZeroBase() {
        // Division-by-zero must not silently emit infinity / crash the grid.
        assertThat(CompPlanningMath.increasePercent(bd("0"), bd("500"))).isNull();
    }

    @Test
    void increasePercentNullForNullInputs() {
        assertThat(CompPlanningMath.increasePercent(null, bd("100"))).isNull();
        assertThat(CompPlanningMath.increasePercent(bd("100"), null)).isNull();
    }

    @Test
    void increasePercentNullForNegativeBase() {
        // A corrupt negative current_salary shouldn't yield a "this raise
        // is 200% bigger than it really is" display.
        assertThat(CompPlanningMath.increasePercent(bd("-100"), bd("100"))).isNull();
    }

    // ── applyPercent() ──────────────────────────────────────────────────

    @Test
    void applyPercentRoundedToTwoDp() {
        // 1000 × (1 + 0.075) = 1075.00
        assertThat(CompPlanningMath.applyPercent(bd("1000"), bd("7.5")))
                .isEqualByComparingTo(bd("1075.00"));
    }

    @Test
    void applyZeroPercentReturnsBase() {
        assertThat(CompPlanningMath.applyPercent(bd("2345.67"), bd("0")))
                .isEqualByComparingTo(bd("2345.67"));
    }

    @Test
    void applyNegativePercentReducesBase() {
        // 5% cut on 2000 = 1900.00
        assertThat(CompPlanningMath.applyPercent(bd("2000"), bd("-5")))
                .isEqualByComparingTo(bd("1900.00"));
    }

    @Test
    void applyPercentNullForNullInputs() {
        assertThat(CompPlanningMath.applyPercent(null, bd("5"))).isNull();
        assertThat(CompPlanningMath.applyPercent(bd("1000"), null)).isNull();
    }

    // ── remaining() ─────────────────────────────────────────────────────

    @Test
    void remainingIsPoolMinusCommitted() {
        assertThat(CompPlanningMath.remaining(bd("100000"), bd("75000")))
                .isEqualByComparingTo(bd("25000"));
    }

    @Test
    void remainingNeverNegative() {
        // Committed exceeds the pool — meter should clamp at zero so the
        // UI's "% used" calculation stays sane. Over-budget gating lives
        // in wouldExceedPool().
        assertThat(CompPlanningMath.remaining(bd("100000"), bd("125000")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void remainingHandlesNullPool() {
        // Defensive — a cycle without a pool returns zero, not NPE.
        assertThat(CompPlanningMath.remaining(null, bd("1000")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void remainingHandlesNullCommitted() {
        assertThat(CompPlanningMath.remaining(bd("50000"), null))
                .isEqualByComparingTo(bd("50000"));
    }

    // ── wouldExceedPool() ───────────────────────────────────────────────

    @Test
    void wouldExceedPoolFalseWhenExactlyOnBudget() {
        // Boundary — exactly the pool size is allowed.
        assertThat(CompPlanningMath.wouldExceedPool(
                bd("100000"), bd("75000"), bd("25000"))).isFalse();
    }

    @Test
    void wouldExceedPoolTrueWhenOnePerCentOver() {
        assertThat(CompPlanningMath.wouldExceedPool(
                bd("100000"), bd("75000"), bd("26000"))).isTrue();
    }

    @Test
    void wouldExceedPoolFalseWhenPoolIsNull() {
        // No pool configured → no enforcement.
        assertThat(CompPlanningMath.wouldExceedPool(
                null, bd("100000"), bd("50000"))).isFalse();
    }

    @Test
    void wouldExceedPoolTreatsNullDeltaAsZero() {
        assertThat(CompPlanningMath.wouldExceedPool(
                bd("100000"), bd("75000"), null)).isFalse();
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
