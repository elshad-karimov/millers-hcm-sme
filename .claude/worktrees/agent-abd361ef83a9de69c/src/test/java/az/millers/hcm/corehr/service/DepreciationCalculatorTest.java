package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import az.millers.hcm.corehr.domain.DepreciationMethod;
import az.millers.hcm.corehr.service.DepreciationCalculator.Period;

/**
 * M128 — pins the depreciation contracts: empty list for NONE / missing
 * inputs, straight-line is flat with a salvage floor on the last month,
 * declining-balance curves down with the same floor, book-value walker
 * picks the right period and floors at salvage past end-of-life.
 */
class DepreciationCalculatorTest {

    private static final LocalDate PURCHASE = LocalDate.of(2026, 1, 15);

    // ── guard rails ────────────────────────────────────────────────────────

    @Test
    void noneMethodReturnsEmpty() {
        List<Period> p = DepreciationCalculator.schedule(
                bd(12000), PURCHASE, 24, bd(0),
                DepreciationMethod.NONE, null);
        assertThat(p).isEmpty();
    }

    @Test
    void nullMethodReturnsEmpty() {
        List<Period> p = DepreciationCalculator.schedule(
                bd(12000), PURCHASE, 24, bd(0), null, null);
        assertThat(p).isEmpty();
    }

    @Test
    void zeroCostReturnsEmpty() {
        List<Period> p = DepreciationCalculator.schedule(
                bd(0), PURCHASE, 24, bd(0),
                DepreciationMethod.STRAIGHT_LINE, null);
        assertThat(p).isEmpty();
    }

    @Test
    void nullPurchaseDateReturnsEmpty() {
        List<Period> p = DepreciationCalculator.schedule(
                bd(12000), null, 24, bd(0),
                DepreciationMethod.STRAIGHT_LINE, null);
        assertThat(p).isEmpty();
    }

    @Test
    void nullOrZeroLifeReturnsEmpty() {
        assertThat(DepreciationCalculator.schedule(
                bd(12000), PURCHASE, null, bd(0),
                DepreciationMethod.STRAIGHT_LINE, null)).isEmpty();
        assertThat(DepreciationCalculator.schedule(
                bd(12000), PURCHASE, 0, bd(0),
                DepreciationMethod.STRAIGHT_LINE, null)).isEmpty();
    }

    // ── straight-line ─────────────────────────────────────────────────────

    @Test
    void straightLineFlatMonthly() {
        // 1200 over 12 months, no salvage → 100/month.
        List<Period> p = DepreciationCalculator.schedule(
                bd(1200), PURCHASE, 12, bd(0),
                DepreciationMethod.STRAIGHT_LINE, null);
        assertThat(p).hasSize(12);
        for (int i = 0; i < 12; i++) {
            assertThat(p.get(i).depreciation()).isEqualByComparingTo("100.00");
        }
        assertThat(p.get(0).openingValue()).isEqualByComparingTo("1200.00");
        assertThat(p.get(11).closingValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void straightLineHonoursSalvage() {
        // 1200 over 12 months, salvage 240 → 80/month, ends at 240.
        List<Period> p = DepreciationCalculator.schedule(
                bd(1200), PURCHASE, 12, bd(240),
                DepreciationMethod.STRAIGHT_LINE, null);
        assertThat(p.get(0).depreciation()).isEqualByComparingTo("80.00");
        assertThat(p.get(11).closingValue()).isEqualByComparingTo("240.00");
    }

    @Test
    void straightLineLastPeriodAbsorbsRoundingCrumbs() {
        // 100 over 3 months → 33.33 with one cent left.
        List<Period> p = DepreciationCalculator.schedule(
                bd(100), PURCHASE, 3, bd(0),
                DepreciationMethod.STRAIGHT_LINE, null);
        assertThat(p.get(0).depreciation()).isEqualByComparingTo("33.33");
        assertThat(p.get(1).depreciation()).isEqualByComparingTo("33.33");
        assertThat(p.get(2).closingValue()).isEqualByComparingTo("0.00");
        // Sum equals cost.
        BigDecimal sum = p.stream().map(Period::depreciation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
    }

    @Test
    void straightLinePeriodStartAdvancesByOneMonth() {
        List<Period> p = DepreciationCalculator.schedule(
                bd(1200), PURCHASE, 4, bd(0),
                DepreciationMethod.STRAIGHT_LINE, null);
        assertThat(p.get(0).periodStart()).isEqualTo(PURCHASE);
        assertThat(p.get(1).periodStart()).isEqualTo(PURCHASE.plusMonths(1));
        assertThat(p.get(3).periodStart()).isEqualTo(PURCHASE.plusMonths(3));
    }

    // ── declining-balance ─────────────────────────────────────────────────

    @Test
    void decliningBalanceCurveDecreasesEarly() {
        // Compare period 1 vs period 12 (well before the last-period
        // snap-to-salvage forces a possible blip). The natural DB curve
        // should monotonically decrease across this range.
        List<Period> p = DepreciationCalculator.schedule(
                bd(12000), PURCHASE, 24, bd(0),
                DepreciationMethod.DECLINING_BALANCE, null);
        assertThat(p.get(0).depreciation())
                .isGreaterThan(p.get(11).depreciation());
        // And period 12 should still be greater than period 18.
        assertThat(p.get(11).depreciation())
                .isGreaterThan(p.get(17).depreciation());
    }

    @Test
    void decliningBalanceFloorsAtSalvage() {
        List<Period> p = DepreciationCalculator.schedule(
                bd(12000), PURCHASE, 12, bd(2000),
                DepreciationMethod.DECLINING_BALANCE, bd("60"));
        // Last period must hit exactly salvage.
        assertThat(p.get(p.size() - 1).closingValue()).isEqualByComparingTo("2000.00");
        // No closing value should ever fall under salvage mid-schedule.
        for (Period period : p) {
            assertThat(period.closingValue()).isGreaterThanOrEqualTo(bd(2000));
        }
    }

    @Test
    void decliningBalanceHonoursExplicitRate() {
        // 1000 over 12 months, rate 12% annual → monthly 1%.
        // Month 1: 1000 * 0.01 = 10.00 dep; closing 990.
        List<Period> p = DepreciationCalculator.schedule(
                bd(1000), PURCHASE, 12, bd(0),
                DepreciationMethod.DECLINING_BALANCE, bd("12"));
        assertThat(p.get(0).depreciation()).isEqualByComparingTo("10.00");
        assertThat(p.get(0).closingValue()).isEqualByComparingTo("990.00");
    }

    // ── bookValueOn ───────────────────────────────────────────────────────

    @Test
    void bookValueOnPurchaseDateEqualsCost() {
        List<Period> p = DepreciationCalculator.schedule(
                bd(1200), PURCHASE, 12, bd(0),
                DepreciationMethod.STRAIGHT_LINE, null);
        BigDecimal book = DepreciationCalculator.bookValueOn(p,
                PURCHASE, bd(1200), bd(0), PURCHASE);
        assertThat(book).isEqualByComparingTo("1200.00");
    }

    @Test
    void bookValueOnPastEndOfLifeFloorsAtSalvage() {
        List<Period> p = DepreciationCalculator.schedule(
                bd(1200), PURCHASE, 12, bd(120),
                DepreciationMethod.STRAIGHT_LINE, null);
        BigDecimal book = DepreciationCalculator.bookValueOn(p,
                PURCHASE, bd(1200), bd(120), PURCHASE.plusYears(5));
        assertThat(book).isEqualByComparingTo("120.00");
    }

    @Test
    void bookValueOnMidScheduleFindsRightPeriod() {
        // 1200 over 12 months → 100/month. At month 4 closing = 1200 - 400 = 800.
        List<Period> p = DepreciationCalculator.schedule(
                bd(1200), PURCHASE, 12, bd(0),
                DepreciationMethod.STRAIGHT_LINE, null);
        BigDecimal book = DepreciationCalculator.bookValueOn(p,
                PURCHASE, bd(1200), bd(0), PURCHASE.plusMonths(3));
        // The 4th period starts at PURCHASE+3m and closes at 800.
        assertThat(book).isEqualByComparingTo("800.00");
    }

    @Test
    void bookValueOnNullScheduleReturnsNull() {
        BigDecimal book = DepreciationCalculator.bookValueOn(
                null, PURCHASE, bd(1200), bd(0), PURCHASE);
        assertThat(book).isNull();
    }

    @Test
    void bookValueOnEmptyScheduleReturnsNull() {
        BigDecimal book = DepreciationCalculator.bookValueOn(
                List.of(), PURCHASE, bd(1200), bd(0), PURCHASE);
        assertThat(book).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static BigDecimal bd(int v) {
        return new BigDecimal(v).setScale(2);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
