package az.millers.hcm.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import az.millers.hcm.attendance.service.ScheduleMath.Metrics;
import az.millers.hcm.attendance.service.ScheduleMath.Window;

/**
 * Pure-math pinning for the attendance engine (M112).
 *
 * <p>Late / early / overtime calculations and cross-midnight window resolution
 * are exactly the kind of math that breaks silently — a manager reviewing
 * weekly summaries doesn't notice that all their night-shift workers
 * suddenly show "absent" until payroll runs. This file covers the four
 * shapes the production code actually exercises:
 *
 * <ul>
 *   <li>Within-day window — 09:00 → 17:00 on day D.</li>
 *   <li>Cross-midnight window — 22:00 D → 06:00 D+1.</li>
 *   <li>Grace period absorbs near-on-time arrivals; lateness past grace shows.</li>
 *   <li>Asymmetric clock-in/out (only IN, only OUT, or neither) produce
 *       safe zeros instead of negative durations.</li>
 * </ul>
 */
class ScheduleMathTest {

    private static final ZoneId Z = ZoneOffset.UTC;
    private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

    // ── window() ────────────────────────────────────────────────────────

    @Test
    void withinDayWindowEndsOnSameDay() {
        Window w = ScheduleMath.window(DAY, LocalTime.of(9, 0), LocalTime.of(17, 0), false, Z);
        assertThat(w.start().toLocalDate()).isEqualTo(DAY);
        assertThat(w.end().toLocalDate()).isEqualTo(DAY);
        assertThat(Duration.between(w.start(), w.end())).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void crossMidnightWindowEndsNextDay() {
        // Night shift 22:00 → 06:00.
        Window w = ScheduleMath.window(DAY, LocalTime.of(22, 0), LocalTime.of(6, 0), true, Z);
        assertThat(w.start().toLocalDate()).isEqualTo(DAY);
        assertThat(w.end().toLocalDate()).isEqualTo(DAY.plusDays(1));
        assertThat(Duration.between(w.start(), w.end())).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void windowRejectsNullInputs() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ScheduleMath.window(null, LocalTime.NOON, LocalTime.NOON, false, Z));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ScheduleMath.window(DAY, null, LocalTime.NOON, false, Z));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ScheduleMath.window(DAY, LocalTime.NOON, null, false, Z));
    }

    // ── eventFetchWindow() ──────────────────────────────────────────────

    @Test
    void eventFetchWindowExpandsBothSides() {
        Window w = ScheduleMath.window(DAY, LocalTime.of(9, 0), LocalTime.of(17, 0), false, Z);
        Window fetch = ScheduleMath.eventFetchWindow(w, Duration.ofHours(4));
        assertThat(Duration.between(w.start(), fetch.start())).isEqualTo(Duration.ofHours(-4));
        assertThat(Duration.between(w.end(), fetch.end())).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void eventFetchWindowZeroSlackIsIdentity() {
        Window w = ScheduleMath.window(DAY, LocalTime.of(9, 0), LocalTime.of(17, 0), false, Z);
        Window fetch = ScheduleMath.eventFetchWindow(w, Duration.ZERO);
        assertThat(fetch.start()).isEqualTo(w.start());
        assertThat(fetch.end()).isEqualTo(w.end());
    }

    @Test
    void eventFetchWindowNullOrNegativeSlackTreatedAsZero() {
        Window w = ScheduleMath.window(DAY, LocalTime.of(9, 0), LocalTime.of(17, 0), false, Z);
        Window nullSlack = ScheduleMath.eventFetchWindow(w, null);
        Window negSlack = ScheduleMath.eventFetchWindow(w, Duration.ofHours(-3));
        assertThat(nullSlack.start()).isEqualTo(w.start());
        assertThat(nullSlack.end()).isEqualTo(w.end());
        assertThat(negSlack.start()).isEqualTo(w.start());
        assertThat(negSlack.end()).isEqualTo(w.end());
    }

    // ── metrics() ───────────────────────────────────────────────────────

    @Test
    void metricsOnTimeArrivalAndExactDeparture() {
        // Exactly on the schedule — no late, no early, no OT.
        OffsetDateTime schStart = at(DAY, 9, 0);
        OffsetDateTime schEnd = at(DAY, 17, 0);
        Metrics m = ScheduleMath.metrics(schStart, schEnd, schStart, schEnd, 60, 5);
        assertThat(m.workedMinutes()).isEqualTo(8 * 60 - 60);  // 480 − 60 break
        assertThat(m.lateMinutes()).isZero();
        assertThat(m.earlyMinutes()).isZero();
        assertThat(m.overtimeMinutes()).isZero();
    }

    @Test
    void metricsLateAbsorbedByGrace() {
        // 3 minutes late with a 5-min grace → 0 late minutes.
        OffsetDateTime schStart = at(DAY, 9, 0);
        OffsetDateTime schEnd = at(DAY, 17, 0);
        Metrics m = ScheduleMath.metrics(
                schStart, schEnd, at(DAY, 9, 3), at(DAY, 17, 0), 60, 5);
        assertThat(m.lateMinutes()).isZero();
    }

    @Test
    void metricsLatePastGraceCounted() {
        // 12 minutes late, 5-min grace → 7 late minutes.
        OffsetDateTime schStart = at(DAY, 9, 0);
        OffsetDateTime schEnd = at(DAY, 17, 0);
        Metrics m = ScheduleMath.metrics(
                schStart, schEnd, at(DAY, 9, 12), at(DAY, 17, 0), 60, 5);
        assertThat(m.lateMinutes()).isEqualTo(7);
    }

    @Test
    void metricsEarlyLeave() {
        // Leaves 20 min before scheduled end.
        OffsetDateTime schStart = at(DAY, 9, 0);
        OffsetDateTime schEnd = at(DAY, 17, 0);
        Metrics m = ScheduleMath.metrics(
                schStart, schEnd, at(DAY, 9, 0), at(DAY, 16, 40), 60, 5);
        assertThat(m.earlyMinutes()).isEqualTo(20);
        assertThat(m.overtimeMinutes()).isZero();
    }

    @Test
    void metricsOvertime() {
        // Stays 45 min past scheduled end.
        OffsetDateTime schStart = at(DAY, 9, 0);
        OffsetDateTime schEnd = at(DAY, 17, 0);
        Metrics m = ScheduleMath.metrics(
                schStart, schEnd, at(DAY, 9, 0), at(DAY, 17, 45), 60, 5);
        assertThat(m.overtimeMinutes()).isEqualTo(45);
        assertThat(m.earlyMinutes()).isZero();
    }

    @Test
    void metricsCrossMidnightWorkedCorrectly() {
        // Night shift 22:00 → 06:00 the next day; clocks in 22:05, out 06:10.
        // 5 min late, 0 grace; worked = 8h05m − 60min break = 425.
        OffsetDateTime schStart = at(DAY, 22, 0);
        OffsetDateTime schEnd = at(DAY.plusDays(1), 6, 0);
        OffsetDateTime in = at(DAY, 22, 5);
        OffsetDateTime out = at(DAY.plusDays(1), 6, 10);
        Metrics m = ScheduleMath.metrics(schStart, schEnd, in, out, 60, 0);
        assertThat(m.workedMinutes()).isEqualTo(8 * 60 + 5 - 60);  // 425
        assertThat(m.lateMinutes()).isEqualTo(5);
        assertThat(m.earlyMinutes()).isZero();
        assertThat(m.overtimeMinutes()).isEqualTo(10);
    }

    @Test
    void metricsNullClockInOrOutReturnZeros() {
        OffsetDateTime schStart = at(DAY, 9, 0);
        OffsetDateTime schEnd = at(DAY, 17, 0);
        Metrics noIn = ScheduleMath.metrics(schStart, schEnd, null, at(DAY, 17, 0), 60, 5);
        Metrics noOut = ScheduleMath.metrics(schStart, schEnd, at(DAY, 9, 0), null, 60, 5);
        Metrics neither = ScheduleMath.metrics(schStart, schEnd, null, null, 60, 5);
        assertThat(noIn.workedMinutes()).isZero();
        assertThat(noIn.lateMinutes()).isZero();
        assertThat(noOut.workedMinutes()).isZero();
        assertThat(neither.workedMinutes()).isZero();
    }

    @Test
    void metricsNullScheduledStartSkipsLateMath() {
        // Edge case: caller has no scheduled start (NONE source). Should
        // never produce a late minute even with a present clock-in.
        OffsetDateTime out = at(DAY, 17, 0);
        Metrics m = ScheduleMath.metrics(null, out, at(DAY, 9, 30), out, 60, 0);
        assertThat(m.lateMinutes()).isZero();
        assertThat(m.earlyMinutes()).isZero();   // null end ⇒ zero early too
        assertThat(m.workedMinutes()).isEqualTo(7 * 60 + 30 - 60);
    }

    @Test
    void metricsNegativeBreakTreatedAsZero() {
        // Defensive — a corrupt break value should not increase worked time.
        OffsetDateTime schStart = at(DAY, 9, 0);
        OffsetDateTime schEnd = at(DAY, 17, 0);
        Metrics m = ScheduleMath.metrics(schStart, schEnd, schStart, schEnd, -60, 0);
        assertThat(m.workedMinutes()).isEqualTo(8 * 60);  // no inflation
    }

    @Test
    void metricsNegativeGraceTreatedAsZero() {
        // Defensive — negative grace mustn't grant retroactive late credit.
        OffsetDateTime schStart = at(DAY, 9, 0);
        OffsetDateTime schEnd = at(DAY, 17, 0);
        Metrics m = ScheduleMath.metrics(
                schStart, schEnd, at(DAY, 9, 5), at(DAY, 17, 0), 60, -10);
        assertThat(m.lateMinutes()).isEqualTo(5);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static OffsetDateTime at(LocalDate d, int h, int m) {
        return d.atTime(h, m).atZone(Z).toOffsetDateTime();
    }
}
