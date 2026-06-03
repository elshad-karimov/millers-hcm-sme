package az.millers.hcm.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Band;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Readiness;

/**
 * Pins the 9-box bucket boundaries and the canonical archetype labels.
 *
 * <p>The bucket boundaries are not arbitrary — a calibration committee
 * relies on the same employee landing in the same cell across re-runs
 * unless their rating crosses the 2.5 / 4.0 thresholds. A regression in
 * either threshold would silently re-shuffle people.
 */
class SuccessionPlanServiceTest {

    // ── Band.of() boundary cases ────────────────────────────────────────────

    @Test
    void lowBoundary() {
        assertThat(Band.of(new BigDecimal("1.00"))).isEqualTo(Band.LOW);
        assertThat(Band.of(new BigDecimal("2.49"))).isEqualTo(Band.LOW);
    }

    @Test
    void midBoundary() {
        assertThat(Band.of(new BigDecimal("2.50"))).isEqualTo(Band.MID);
        assertThat(Band.of(new BigDecimal("3.99"))).isEqualTo(Band.MID);
    }

    @Test
    void highBoundary() {
        assertThat(Band.of(new BigDecimal("4.00"))).isEqualTo(Band.HIGH);
        assertThat(Band.of(new BigDecimal("5.00"))).isEqualTo(Band.HIGH);
    }

    @Test
    void nullRatingThrows() {
        assertThatThrownBy(() -> Band.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Archetype labels ────────────────────────────────────────────────────

    @Test
    void highPotentialHighPerformanceIsStar() {
        assertThat(SuccessionPlanService.labelFor(Band.HIGH, Band.HIGH)).isEqualTo("Star");
    }

    @Test
    void lowPotentialLowPerformanceIsIceberg() {
        assertThat(SuccessionPlanService.labelFor(Band.LOW, Band.LOW)).isEqualTo("Iceberg");
    }

    @Test
    void highPerformanceLowPotentialIsTrustedPro() {
        assertThat(SuccessionPlanService.labelFor(Band.HIGH, Band.LOW)).isEqualTo("Trusted Pro");
    }

    @Test
    void lowPerformanceHighPotentialIsEnigma() {
        // The classic "promote on potential alone" warning sign.
        assertThat(SuccessionPlanService.labelFor(Band.LOW, Band.HIGH)).isEqualTo("Enigma");
    }

    @Test
    void midMidIsCorePlayer() {
        assertThat(SuccessionPlanService.labelFor(Band.MID, Band.MID)).isEqualTo("Core Player");
    }

    @Test
    void allNineLabelsAreDistinct() {
        // No two cells should share a label — the grid is a partition.
        java.util.Set<String> labels = new java.util.HashSet<>();
        for (Band p : Band.values()) {
            for (Band q : Band.values()) {
                labels.add(SuccessionPlanService.labelFor(p, q));
            }
        }
        assertThat(labels).hasSize(9);
    }

    // ── Readiness tier (M94) ────────────────────────────────────────────────

    @Test
    void starsAreReadyNow() {
        assertThat(SuccessionPlanService.readinessFor(Band.HIGH, Band.HIGH))
                .isEqualTo(Readiness.READY_NOW);
    }

    @Test
    void futureStarsAndHighPerformersAreReadySoon() {
        // Future Star: MID perf × HIGH pot
        assertThat(SuccessionPlanService.readinessFor(Band.MID, Band.HIGH))
                .isEqualTo(Readiness.READY_SOON);
        // High Performer: HIGH perf × MID pot
        assertThat(SuccessionPlanService.readinessFor(Band.HIGH, Band.MID))
                .isEqualTo(Readiness.READY_SOON);
    }

    @Test
    void corePlayersAndTrustedProsAreLongTermBench() {
        // Core Player: MID/MID
        assertThat(SuccessionPlanService.readinessFor(Band.MID, Band.MID))
                .isEqualTo(Readiness.READY_LONG_TERM);
        // Trusted Pro: HIGH perf × LOW pot — strong contributor, not a successor
        assertThat(SuccessionPlanService.readinessFor(Band.HIGH, Band.LOW))
                .isEqualTo(Readiness.READY_LONG_TERM);
    }

    @Test
    void lowPerformanceIsUnderDevelopmentRegardlessOfPotential() {
        // Enigma: LOW perf × HIGH pot — "promote on potential alone" trap
        assertThat(SuccessionPlanService.readinessFor(Band.LOW, Band.HIGH))
                .isEqualTo(Readiness.UNDER_DEVELOPMENT);
        // Inconsistent Player: LOW/MID
        assertThat(SuccessionPlanService.readinessFor(Band.LOW, Band.MID))
                .isEqualTo(Readiness.UNDER_DEVELOPMENT);
        // Iceberg: LOW/LOW
        assertThat(SuccessionPlanService.readinessFor(Band.LOW, Band.LOW))
                .isEqualTo(Readiness.UNDER_DEVELOPMENT);
    }

    @Test
    void midPerformanceLowPotentialIsUnderDevelopment() {
        // Solid Performer — productive but not on the bench.
        assertThat(SuccessionPlanService.readinessFor(Band.MID, Band.LOW))
                .isEqualTo(Readiness.UNDER_DEVELOPMENT);
    }

    @Test
    void everyCellHasExactlyOneReadinessTier() {
        // Each of the 9 cells maps to exactly one readiness tier — the
        // mapping is a total function with no gaps or duplicates.
        int count = 0;
        for (Band p : Band.values()) {
            for (Band q : Band.values()) {
                assertThat(SuccessionPlanService.readinessFor(p, q))
                        .as("perf=%s pot=%s", p, q)
                        .isNotNull();
                count++;
            }
        }
        assertThat(count).isEqualTo(9);
    }
}
