package az.millers.hcm.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/**
 * M126 — pure-static SLA math. Pins the "null/zero/negative SLA never
 * breaches" rule that callers rely on to opt steps out of enforcement,
 * plus the rounding behaviour of {@link SlaCalculator#hoursOverdue} and
 * {@link SlaCalculator#percentConsumed}.
 */
class SlaCalculatorTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-06-05T09:00:00Z");

    // ── dueAt ────────────────────────────────────────────────────────────

    @Test
    void dueAtAddsHours() {
        assertThat(SlaCalculator.dueAt(T0, 24))
                .isEqualTo(OffsetDateTime.parse("2026-06-06T09:00:00Z"));
    }

    @Test
    void dueAtReturnsNullForNullInputs() {
        assertThat(SlaCalculator.dueAt(null, 24)).isNull();
        assertThat(SlaCalculator.dueAt(T0, null)).isNull();
    }

    @Test
    void dueAtReturnsNullForZeroOrNegative() {
        assertThat(SlaCalculator.dueAt(T0, 0)).isNull();
        assertThat(SlaCalculator.dueAt(T0, -5)).isNull();
    }

    // ── isBreached ────────────────────────────────────────────────────────

    @Test
    void isBreachedStrictlyAfterDue() {
        // Exactly at the due time → NOT breached. One minute past → breached.
        OffsetDateTime due = T0.plusHours(24);
        assertThat(SlaCalculator.isBreached(T0, 24, due)).isFalse();
        assertThat(SlaCalculator.isBreached(T0, 24, due.plusMinutes(1))).isTrue();
    }

    @Test
    void isBreachedFalseWhenNoSla() {
        assertThat(SlaCalculator.isBreached(T0, null, T0.plusYears(1))).isFalse();
        assertThat(SlaCalculator.isBreached(T0, 0, T0.plusYears(1))).isFalse();
        assertThat(SlaCalculator.isBreached(T0, -3, T0.plusYears(1))).isFalse();
    }

    @Test
    void isBreachedFalseWhenInputsNull() {
        assertThat(SlaCalculator.isBreached(null, 24, T0)).isFalse();
        assertThat(SlaCalculator.isBreached(T0, 24, null)).isFalse();
    }

    // ── hoursOverdue ──────────────────────────────────────────────────────

    @Test
    void hoursOverdueZeroWhenNotBreached() {
        assertThat(SlaCalculator.hoursOverdue(T0, 24, T0.plusHours(10)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void hoursOverdueExactValueWhenBreached() {
        // 24h SLA, now = +30h → 6h overdue.
        assertThat(SlaCalculator.hoursOverdue(T0, 24, T0.plusHours(30)))
                .isEqualByComparingTo("6.00");
    }

    @Test
    void hoursOverdueRoundedToTwoDp() {
        // 24h SLA, now = +25h 7min → 1h 7min overdue → 1.12.
        assertThat(SlaCalculator.hoursOverdue(T0, 24, T0.plusHours(25).plusMinutes(7)))
                .isEqualByComparingTo("1.12");
    }

    @Test
    void hoursOverdueZeroWhenNoSla() {
        assertThat(SlaCalculator.hoursOverdue(T0, null, T0.plusYears(1)))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(SlaCalculator.hoursOverdue(T0, 0, T0.plusYears(1)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── percentConsumed ──────────────────────────────────────────────────

    @Test
    void percentConsumedAtHalf() {
        assertThat(SlaCalculator.percentConsumed(T0, 24, T0.plusHours(12)))
                .isEqualByComparingTo("50.0");
    }

    @Test
    void percentConsumedExceedsHundredWhenBreached() {
        // 24h SLA, now = +36h → 150%.
        assertThat(SlaCalculator.percentConsumed(T0, 24, T0.plusHours(36)))
                .isEqualByComparingTo("150.0");
    }

    @Test
    void percentConsumedZeroBeforeEntry() {
        // now < stepEnteredAt — possible clock-skew case. We treat
        // it as 0 rather than returning a negative.
        assertThat(SlaCalculator.percentConsumed(T0, 24, T0.minusHours(1)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void percentConsumedZeroWhenNoSla() {
        assertThat(SlaCalculator.percentConsumed(T0, null, T0.plusHours(1)))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(SlaCalculator.percentConsumed(T0, 0, T0.plusHours(1)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void percentConsumedZeroWhenInputsNull() {
        assertThat(SlaCalculator.percentConsumed(null, 24, T0)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(SlaCalculator.percentConsumed(T0, 24, null)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
