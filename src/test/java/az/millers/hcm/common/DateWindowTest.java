package az.millers.hcm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * Pins the half-open semantics of {@link DateWindow}. Three analytics methods
 * in {@code RecruitmentAnalyticsService} (and any future analytics surface)
 * depend on these boundaries — a slip from "[from, to+1) " to "[from, to]"
 * would silently double-count the last day.
 */
class DateWindowTest {

    @Test
    void fromTsIsStartOfFromDayUtc() {
        DateWindow w = DateWindow.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(w.fromTs()).isEqualTo(
                OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toTsIsStartOfDayAFTERToDay() {
        DateWindow w = DateWindow.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        // Half-open: the upper bound is exclusive at the start of July 1st.
        assertThat(w.toTsExclusive()).isEqualTo(
                OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void containsRespectsHalfOpenInterval() {
        DateWindow w = DateWindow.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        // Inside
        assertThat(w.contains(OffsetDateTime.of(2026, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC))).isTrue();
        // Exact lower bound — included
        assertThat(w.contains(w.fromTs())).isTrue();
        // Exact upper bound — EXCLUDED (this is the half-open contract)
        assertThat(w.contains(w.toTsExclusive())).isFalse();
        // Just before upper bound — included
        assertThat(w.contains(w.toTsExclusive().minusNanos(1))).isTrue();
        // Before lower bound
        assertThat(w.contains(OffsetDateTime.of(2026, 5, 31, 23, 59, 59, 0, ZoneOffset.UTC))).isFalse();
    }

    @Test
    void singleDayWindowIs24Hours() {
        DateWindow w = DateWindow.of(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15));
        assertThat(w.toTsExclusive()).isEqualTo(w.fromTs().plusDays(1));
        assertThat(w.contains(OffsetDateTime.of(2026, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC))).isTrue();
    }

    @Test
    void ofRejectsReversedBoundaries() {
        assertThatThrownBy(() ->
                DateWindow.of(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofOrDefaultSubtractsPeriodFromTo() {
        // Both null → today - 1 year, today
        LocalDate today = LocalDate.now();
        DateWindow w = DateWindow.ofOrDefault(null, null, Period.ofYears(1));
        assertThat(w.to()).isEqualTo(today);
        assertThat(w.from()).isEqualTo(today.minus(Period.ofYears(1)));
    }

    @Test
    void ofOrDefaultRespectsExplicitFrom() {
        DateWindow w = DateWindow.ofOrDefault(LocalDate.of(2026, 1, 1), null, Period.ofYears(1));
        assertThat(w.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(w.to()).isEqualTo(LocalDate.now());
    }
}
