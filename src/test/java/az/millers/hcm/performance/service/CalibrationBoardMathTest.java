package az.millers.hcm.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;

/**
 * M121 — pure-static math the calibration board v2 leans on:
 * <ul>
 *   <li>{@link CalibrationBoardMath#validateTargets} rejects unknown bands,
 *       sums &gt; 100%, and negatives, all with useful messages,</li>
 *   <li>{@link CalibrationBoardMath#buildBoardDistribution} produces
 *       actual%, target%, and delta correctly, in canonical order, and
 *       degrades gracefully when totals or targets are missing.</li>
 * </ul>
 */
class CalibrationBoardMathTest {

    private static Map<String, BigDecimal> tgt(Object... kv) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], new BigDecimal(kv[i + 1].toString()));
        }
        return m;
    }

    // ── validateTargets ─────────────────────────────────────────────────────

    @Test
    void acceptsExactSumOf100() {
        CalibrationBoardMath.validateTargets(tgt(
                "5 - Exceptional", 10,
                "4 - Exceeds", 30,
                "3 - Meets", 40,
                "2 - Needs Improvement", 15,
                "1 - Unsatisfactory", 5));
    }

    @Test
    void acceptsSumBelow100() {
        // HR might intentionally leave "Unrated" as the residual.
        CalibrationBoardMath.validateTargets(tgt(
                "5 - Exceptional", 10,
                "4 - Exceeds", 30,
                "3 - Meets", 40));
    }

    @Test
    void rejectsSumOver100() {
        assertThatThrownBy(() -> CalibrationBoardMath.validateTargets(tgt(
                "5 - Exceptional", 50,
                "4 - Exceeds", 60)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not exceed 100%");
    }

    @Test
    void rejectsUnknownBand() {
        assertThatThrownBy(() -> CalibrationBoardMath.validateTargets(tgt(
                "Awesome", 20)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown band: Awesome");
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> CalibrationBoardMath.validateTargets(tgt(
                "3 - Meets", -5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be ≥ 0");
    }

    @Test
    void rejectsBandOver100() {
        assertThatThrownBy(() -> CalibrationBoardMath.validateTargets(tgt(
                "3 - Meets", 110)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot exceed 100");
    }

    @Test
    void rejectsEmptyMap() {
        assertThatThrownBy(() -> CalibrationBoardMath.validateTargets(new LinkedHashMap<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("At least one band");
    }

    @Test
    void rejectsNullValue() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        m.put("3 - Meets", null);
        assertThatThrownBy(() -> CalibrationBoardMath.validateTargets(m))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("required");
    }

    // ── buildBoardDistribution ──────────────────────────────────────────────

    @Test
    void computesActualPctAndDelta() {
        Map<String, Long> actual = new LinkedHashMap<>();
        actual.put("5 - Exceptional", 1L);
        actual.put("4 - Exceeds", 3L);
        actual.put("3 - Meets", 4L);
        actual.put("2 - Needs Improvement", 2L);
        // total = 10
        Map<String, BigDecimal> targets = tgt(
                "5 - Exceptional", 10,
                "4 - Exceeds", 30,
                "3 - Meets", 40,
                "2 - Needs Improvement", 15,
                "1 - Unsatisfactory", 5);

        Map<String, CalibrationBoardMath.BoardCell> result =
                CalibrationBoardMath.buildBoardDistribution(actual, targets, 10);

        CalibrationBoardMath.BoardCell five = result.get("5 - Exceptional");
        assertThat(five.actualCount()).isEqualTo(1L);
        assertThat(five.actualPercent()).isEqualByComparingTo("10.00");
        assertThat(five.targetPercent()).isEqualByComparingTo("10");
        assertThat(five.delta()).isEqualByComparingTo("0.00");

        CalibrationBoardMath.BoardCell four = result.get("4 - Exceeds");
        assertThat(four.actualPercent()).isEqualByComparingTo("30.00");
        assertThat(four.delta()).isEqualByComparingTo("0.00");

        CalibrationBoardMath.BoardCell one = result.get("1 - Unsatisfactory");
        // No actuals in this band; actualPct = 0, target was 5 → delta = -5.
        assertThat(one.actualCount()).isEqualTo(0L);
        assertThat(one.actualPercent()).isEqualByComparingTo("0.00");
        assertThat(one.delta()).isEqualByComparingTo("-5.00");
    }

    @Test
    void emitsZeroCellsForEveryCanonicalBandWhenZeroReviews() {
        Map<String, CalibrationBoardMath.BoardCell> result =
                CalibrationBoardMath.buildBoardDistribution(Map.of(), tgt("3 - Meets", 40), 0);

        assertThat(result.keySet()).containsAll(CalibrationBoardMath.CANONICAL_BANDS);
        // With zero totals the cells carry actualCount=0 and target preserved.
        CalibrationBoardMath.BoardCell meets = result.get("3 - Meets");
        assertThat(meets.actualCount()).isEqualTo(0L);
        assertThat(meets.targetPercent()).isEqualByComparingTo("40");
    }

    @Test
    void leavesDeltaNullWhenTargetMissing() {
        Map<String, Long> actual = new LinkedHashMap<>();
        actual.put("5 - Exceptional", 1L);
        Map<String, CalibrationBoardMath.BoardCell> result =
                CalibrationBoardMath.buildBoardDistribution(actual, Map.of(), 1);
        assertThat(result.get("5 - Exceptional").delta()).isNull();
    }

    @Test
    void canonicalBandsAppearInDisplayOrder() {
        Map<String, CalibrationBoardMath.BoardCell> result =
                CalibrationBoardMath.buildBoardDistribution(Map.of(), Map.of(), 0);
        assertThat(result.keySet()).containsExactlyElementsOf(CalibrationBoardMath.CANONICAL_BANDS);
    }

    @Test
    void unknownBandsInActualsLandAfterCanonical() {
        Map<String, Long> actual = new LinkedHashMap<>();
        actual.put("3 - Meets", 1L);
        actual.put("Unrated", 1L);
        Map<String, CalibrationBoardMath.BoardCell> result =
                CalibrationBoardMath.buildBoardDistribution(actual, Map.of(), 2);
        assertThat(result.keySet()).contains("Unrated");
        // Unrated comes after the canonical five.
        var keys = result.keySet().stream().toList();
        assertThat(keys.indexOf("Unrated")).isGreaterThan(keys.indexOf("1 - Unsatisfactory"));
    }
}
