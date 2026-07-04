package az.millers.hcm.leave.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.leave.service.LeaveOverlapAnalyzer.LeaveInterval;

/**
 * M131 — pins the team-calendar math: how many people are out on day X,
 * which days exceed the concurrent-absence threshold, and what %-off
 * each day represents.
 *
 * <p>These are the only places production code makes a judgment call
 * about overlap. Mockito-free because Java 25 class-file v69 isn't
 * supported by Byte Buddy.
 */
class LeaveOverlapAnalyzerTest {

    private static final LocalDate JUN_1  = LocalDate.of(2026, 6, 1);
    private static final LocalDate JUN_5  = LocalDate.of(2026, 6, 5);
    private static final LocalDate JUN_10 = LocalDate.of(2026, 6, 10);
    private static final LocalDate JUN_30 = LocalDate.of(2026, 6, 30);
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB   = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();

    // ── expandRange ──────────────────────────────────────────────────────

    @Test
    void expandRangeInclusive() {
        var days = LeaveOverlapAnalyzer.expandRange(JUN_1, LocalDate.of(2026, 6, 3));
        assertThat(days).containsExactly(JUN_1, JUN_1.plusDays(1), JUN_1.plusDays(2));
    }

    @Test
    void expandRangeEmptyWhenInverted() {
        assertThat(LeaveOverlapAnalyzer.expandRange(JUN_10, JUN_5)).isEmpty();
    }

    @Test
    void expandRangeEmptyWhenNullEnd() {
        assertThat(LeaveOverlapAnalyzer.expandRange(JUN_1, null)).isEmpty();
    }

    // ── dailyCounts ──────────────────────────────────────────────────────

    @Test
    void dailyCountsCountsEachOverlapOnce() {
        var i = List.of(
                new LeaveInterval(ALICE, JUN_1, JUN_5, true),
                // Same employee, overlapping range — must count once per day.
                new LeaveInterval(ALICE, JUN_5, JUN_10, true),
                new LeaveInterval(BOB,   JUN_5, JUN_5, true));
        var counts = LeaveOverlapAnalyzer.dailyCounts(i, JUN_1, JUN_10);
        // JUN_5 = Alice + Bob = 2.
        assertThat(counts.get(JUN_5)).isEqualTo(2);
        // JUN_1 = Alice only = 1.
        assertThat(counts.get(JUN_1)).isEqualTo(1);
        // JUN_10 = Alice only = 1.
        assertThat(counts.get(JUN_10)).isEqualTo(1);
    }

    @Test
    void dailyCountsClipsToWindow() {
        // Leave spans Jun 1 – Jun 30 but window is Jun 5 – Jun 10.
        var i = List.of(new LeaveInterval(ALICE, JUN_1, JUN_30, true));
        var counts = LeaveOverlapAnalyzer.dailyCounts(i, JUN_5, JUN_10);
        assertThat(counts).hasSize(6);
        assertThat(counts.values()).allMatch(v -> v == 1);
    }

    @Test
    void dailyCountsZeroForDayWithNoLeave() {
        var counts = LeaveOverlapAnalyzer.dailyCounts(List.of(), JUN_1, JUN_5);
        assertThat(counts).hasSize(5);
        assertThat(counts.values()).allMatch(v -> v == 0);
    }

    @Test
    void dailyCountsPreservesDayOrder() {
        var counts = LeaveOverlapAnalyzer.dailyCounts(List.of(), JUN_1, JUN_5);
        assertThat(counts.keySet()).containsExactly(
                JUN_1, JUN_1.plusDays(1), JUN_1.plusDays(2), JUN_1.plusDays(3), JUN_1.plusDays(4));
    }

    @Test
    void dailyCountsIgnoresOutOfWindow() {
        var i = List.of(new LeaveInterval(ALICE, JUN_1, JUN_5, true));
        var counts = LeaveOverlapAnalyzer.dailyCounts(i, JUN_10, JUN_30);
        assertThat(counts.values()).allMatch(v -> v == 0);
    }

    // ── flagDays ─────────────────────────────────────────────────────────

    @Test
    void flagDaysAboveThreshold() {
        Map<LocalDate, Integer> counts = Map.of(
                JUN_1, 1,
                JUN_5, 3,
                JUN_10, 2);
        // Team of 5, threshold 40% → flagged = days where outCount/5 ≥ 40%.
        // JUN_5: 3/5 = 60% → flag. JUN_10: 2/5 = 40% → flag (boundary).
        var flagged = LeaveOverlapAnalyzer.flagDays(counts, 5, BigDecimal.valueOf(40));
        assertThat(flagged).containsExactlyInAnyOrder(JUN_5, JUN_10);
    }

    @Test
    void flagDaysEmptyWhenTeamTooSmall() {
        Map<LocalDate, Integer> counts = Map.of(JUN_1, 1);
        var flagged = LeaveOverlapAnalyzer.flagDays(counts, 1, BigDecimal.valueOf(40));
        assertThat(flagged).isEmpty();
    }

    @Test
    void flagDaysEmptyWhenThresholdZero() {
        Map<LocalDate, Integer> counts = Map.of(JUN_1, 1, JUN_5, 5);
        var flagged = LeaveOverlapAnalyzer.flagDays(counts, 5, BigDecimal.ZERO);
        assertThat(flagged).isEmpty();
    }

    @Test
    void flagDaysEmptyOnNullCounts() {
        assertThat(LeaveOverlapAnalyzer.flagDays(null, 5, BigDecimal.valueOf(40))).isEmpty();
    }

    // ── percentOff ───────────────────────────────────────────────────────

    @Test
    void percentOffComputesScale2() {
        assertThat(LeaveOverlapAnalyzer.percentOff(3, 5)).isEqualByComparingTo("60.00");
        assertThat(LeaveOverlapAnalyzer.percentOff(1, 3)).isEqualByComparingTo("33.33");
        assertThat(LeaveOverlapAnalyzer.percentOff(0, 5)).isEqualByComparingTo("0.00");
    }

    @Test
    void percentOffZeroForEmptyTeam() {
        assertThat(LeaveOverlapAnalyzer.percentOff(2, 0)).isEqualByComparingTo("0.00");
    }

    @Test
    void multiEmployeeOverlappingIntervalsCountedCorrectly() {
        var i = List.of(
                new LeaveInterval(ALICE, JUN_5, JUN_10, true),
                new LeaveInterval(BOB,   JUN_5, JUN_10, false), // PENDING still counts
                new LeaveInterval(CARLA, JUN_10, JUN_10, true));
        var counts = LeaveOverlapAnalyzer.dailyCounts(i, JUN_1, JUN_10);
        assertThat(counts.get(JUN_5)).isEqualTo(2);  // Alice + Bob
        assertThat(counts.get(JUN_10)).isEqualTo(3); // Alice + Bob + Carla
    }
}
