package az.millers.hcm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Pins the single leave-day valuation used by leave liability/encashment/
 * unpaid-deduction and final-settlement. Guards against a re-introduction of the
 * old ÷22-vs-÷21.67 divisor drift.
 */
class LeaveDayValuationTest {

    @Test
    void defaultDivisorIs22() {
        assertThat(LeaveDayValuation.DEFAULT_WORKING_DAYS_PER_MONTH).isEqualTo(22);
    }

    @Test
    void dailyRateDividesBy22_scale4HalfUp() {
        // 2200 / 22 = 100.0000
        assertThat(LeaveDayValuation.dailyRate(new BigDecimal("2200.00")))
                .isEqualByComparingTo("100.0000");
        // 3000 / 22 = 136.363636… → HALF_UP scale 4 → 136.3636
        assertThat(LeaveDayValuation.dailyRate(new BigDecimal("3000.00")))
                .isEqualByComparingTo("136.3636");
    }

    @Test
    void dailyRateUses22Not21_67() {
        // Regression lock: with ÷22 this is 98.50; the old ÷21.67 gave 100.0000.
        assertThat(LeaveDayValuation.dailyRate(new BigDecimal("2167.00")))
                .isEqualByComparingTo("98.5000");
    }

    @Test
    void nonPositiveInputsAreZeroOrFallBack() {
        assertThat(LeaveDayValuation.dailyRate(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(LeaveDayValuation.dailyRate(null)).isEqualByComparingTo("0");
        // workingDaysPerMonth <= 0 falls back to the default 22 → 2200/22 = 100
        assertThat(LeaveDayValuation.dailyRate(new BigDecimal("2200.00"), 0))
                .isEqualByComparingTo("100.0000");
    }

    @Test
    void valueOfDaysMultipliesThenRoundsScale2() {
        // 2200/22 = 100.0000 × 5 = 500.00
        assertThat(LeaveDayValuation.valueOfDays(new BigDecimal("2200.00"), 22, new BigDecimal("5")))
                .isEqualByComparingTo("500.00");
    }
}
