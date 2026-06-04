package az.millers.hcm.compbenefits.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import az.millers.hcm.compbenefits.api.dto.CompRatioDtos.CompRiskLevel;
import az.millers.hcm.staffing.domain.Grade;

/**
 * Pins the comp-ratio bucket thresholds and midpoint calculation (M102).
 *
 * <p>Flight-risk employees (ratio &lt; 80) and equity-concern employees
 * (ratio &gt; 120) are the two signals HR acts on; any regression in the
 * boundaries would silently re-classify employees.
 */
class CompRatioServiceTest {

    // ── riskLevel() boundaries ──────────────────────────────────────────────

    @Test
    void below80IsFlightRisk() {
        assertThat(CompRatioService.riskLevel(bd("79.99"))).isEqualTo(CompRiskLevel.BELOW_RANGE);
        assertThat(CompRatioService.riskLevel(bd("0"))).isEqualTo(CompRiskLevel.BELOW_RANGE);
    }

    @Test
    void exactly80IsLowInRange() {
        assertThat(CompRatioService.riskLevel(bd("80.00"))).isEqualTo(CompRiskLevel.LOW_IN_RANGE);
        assertThat(CompRatioService.riskLevel(bd("89.99"))).isEqualTo(CompRiskLevel.LOW_IN_RANGE);
    }

    @Test
    void from90to110IsAtMidpoint() {
        assertThat(CompRatioService.riskLevel(bd("90.00"))).isEqualTo(CompRiskLevel.AT_MIDPOINT);
        assertThat(CompRatioService.riskLevel(bd("100"))).isEqualTo(CompRiskLevel.AT_MIDPOINT);
        assertThat(CompRatioService.riskLevel(bd("110.00"))).isEqualTo(CompRiskLevel.AT_MIDPOINT);
    }

    @Test
    void from110to120IsHighInRange() {
        assertThat(CompRatioService.riskLevel(bd("110.01"))).isEqualTo(CompRiskLevel.HIGH_IN_RANGE);
        assertThat(CompRatioService.riskLevel(bd("120.00"))).isEqualTo(CompRiskLevel.HIGH_IN_RANGE);
    }

    @Test
    void above120IsAboveRange() {
        assertThat(CompRatioService.riskLevel(bd("120.01"))).isEqualTo(CompRiskLevel.ABOVE_RANGE);
        assertThat(CompRatioService.riskLevel(bd("200"))).isEqualTo(CompRiskLevel.ABOVE_RANGE);
    }

    @Test
    void nullRatioIsNoBand() {
        assertThat(CompRatioService.riskLevel(null)).isEqualTo(CompRiskLevel.NO_BAND);
    }

    // ── midpoint() ────────────────────────────────────────────────────────

    @Test
    void midpointAveragesMinAndMax() {
        Grade g = grade("3000", "5000");
        assertThat(CompRatioService.midpoint(g)).isEqualByComparingTo("4000");
    }

    @Test
    void midpointWithNoMinReturnsMax() {
        Grade g = grade(null, "5000");
        assertThat(CompRatioService.midpoint(g)).isEqualByComparingTo("5000");
    }

    @Test
    void midpointWithNoMaxReturnsMin() {
        Grade g = grade("3000", null);
        assertThat(CompRatioService.midpoint(g)).isEqualByComparingTo("3000");
    }

    @Test
    void midpointWithNoBoundsIsNull() {
        Grade g = grade(null, null);
        assertThat(CompRatioService.midpoint(g)).isNull();
    }

    @Test
    void midpointIsHalfUp() {
        // 1000 + 2001 = 3001 / 2 = 1500.5 → 1500.50 (half-up)
        Grade g = grade("1000", "2001");
        assertThat(CompRatioService.midpoint(g)).isEqualByComparingTo("1500.50");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Grade grade(String min, String max) {
        Grade g = new Grade();
        g.setMinSalary(min == null ? null : new BigDecimal(min));
        g.setMaxSalary(max == null ? null : new BigDecimal(max));
        return g;
    }
}
