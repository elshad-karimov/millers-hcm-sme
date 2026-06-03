package az.millers.hcm.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Pins the date-delta + message helpers used by {@link PathReminderScheduler}
 * (M97). Mockito-free — the helpers are package-private static so the
 * test can drive them directly, in the same style as the M89 / M92 / M94
 * tests.
 *
 * <p>The delta math is signed (positive = future, negative = overdue) and a
 * regression that flipped the sign would silently move the manager-escalation
 * branch — that's where the dedicated boundary tests pay off.
 */
class PathReminderSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 3);

    // ── daysBetween ────────────────────────────────────────────────────────

    @Test
    void todayEqualsTargetIsZero() {
        assertThat(PathReminderScheduler.daysBetween(TODAY, TODAY)).isZero();
    }

    @Test
    void futureTargetIsPositive() {
        assertThat(PathReminderScheduler.daysBetween(TODAY, TODAY.plusDays(7))).isEqualTo(7);
    }

    @Test
    void pastTargetIsNegative() {
        // Overdue: today is AFTER target → delta is negative.
        assertThat(PathReminderScheduler.daysBetween(TODAY, TODAY.minusDays(7))).isEqualTo(-7);
    }

    @Test
    void nullInputIsSentinel() {
        // Defensive — returns Integer.MIN_VALUE so the reminder filter rejects it.
        assertThat(PathReminderScheduler.daysBetween(TODAY, null)).isEqualTo(Integer.MIN_VALUE);
        assertThat(PathReminderScheduler.daysBetween(null, TODAY)).isEqualTo(Integer.MIN_VALUE);
    }

    // ── isReminderDelta ────────────────────────────────────────────────────

    @Test
    void deltaInConfigFiresReminder() {
        int[] cfg = {30, 14, 7, 1, 0, -7, -30};
        assertThat(PathReminderScheduler.isReminderDelta(7, cfg)).isTrue();
        assertThat(PathReminderScheduler.isReminderDelta(-7, cfg)).isTrue();
        assertThat(PathReminderScheduler.isReminderDelta(0, cfg)).isTrue();
    }

    @Test
    void deltaNotInConfigDoesNotFire() {
        int[] cfg = {30, 14, 7, 1, 0, -7, -30};
        assertThat(PathReminderScheduler.isReminderDelta(8, cfg)).isFalse();
        assertThat(PathReminderScheduler.isReminderDelta(-1, cfg)).isFalse();
        assertThat(PathReminderScheduler.isReminderDelta(100, cfg)).isFalse();
    }

    @Test
    void sentinelDeltaIsRejected() {
        int[] cfg = {30, 14, 7, 1, 0, -7, -30};
        // From a null targetCompletionDate via daysBetween.
        assertThat(PathReminderScheduler.isReminderDelta(Integer.MIN_VALUE, cfg)).isFalse();
    }

    // ── Title + body templates ─────────────────────────────────────────────

    @Test
    void titleUsesPluralAppropriately() {
        assertThat(PathReminderScheduler.buildTitle(1, "Leadership"))
                .isEqualTo("Learning path \"Leadership\" due in 1 day");
        assertThat(PathReminderScheduler.buildTitle(7, "Leadership"))
                .isEqualTo("Learning path \"Leadership\" due in 7 days");
    }

    @Test
    void titleAtZeroIsDueToday() {
        assertThat(PathReminderScheduler.buildTitle(0, "Leadership"))
                .isEqualTo("Learning path \"Leadership\" due today");
    }

    @Test
    void titleNegativeShowsAbsoluteOverdue() {
        // -7 days means "overdue by 7 days" — show 7, not -7.
        assertThat(PathReminderScheduler.buildTitle(-1, "Leadership"))
                .isEqualTo("Learning path \"Leadership\" is 1 day overdue");
        assertThat(PathReminderScheduler.buildTitle(-7, "Leadership"))
                .isEqualTo("Learning path \"Leadership\" is 7 days overdue");
    }

    @Test
    void bodyIncludesNotesWhenPresent() {
        String body = PathReminderScheduler.buildBody(7, "Leadership",
                LocalDate.of(2026, 6, 10), "Linked to 2026 perf review");
        assertThat(body).contains("Linked to 2026 perf review");
        assertThat(body).contains("2026-06-10");
        assertThat(body).contains("Review progress at /learning/paths.");
    }

    @Test
    void bodyOmitsNotesWhenBlank() {
        String body = PathReminderScheduler.buildBody(0, "Leadership",
                LocalDate.of(2026, 6, 3), "");
        assertThat(body).doesNotContain("Assignment notes:");
    }
}
