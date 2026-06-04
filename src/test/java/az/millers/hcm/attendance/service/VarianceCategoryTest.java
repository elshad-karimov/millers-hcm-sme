package az.millers.hcm.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.SummaryStatus;

/**
 * Pins the M113 variance categorizer.
 *
 * <p>The mapping decides which employees show up as "offenders" on the
 * dashboard. Off-by-one priority (e.g. counting a LATE row as ON_TIME
 * because lateMinutes is set but the status is PRESENT) would silently
 * hide actionable signal from managers.
 */
class VarianceCategoryTest {

    @Test
    void nonRosterSourceIsNotApplicable() {
        // Pre-M110 schedule-driven rows shouldn't appear in the report at all.
        DailySummary s = row("SCHEDULE", SummaryStatus.ABSENT, 0, 0, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.NOT_APPLICABLE);
    }

    @Test
    void noScheduleOrRosterIsNotApplicable() {
        DailySummary s = row("NONE", SummaryStatus.NO_SCHEDULE, 0, 0, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.NOT_APPLICABLE);
    }

    @Test
    void nonWorkingDayIsNotApplicable() {
        // Roster never produces NON_WORKING_DAY, but defensive: a corrupted
        // row with that status mustn't be counted as a no-show.
        DailySummary s = row("ROSTER", SummaryStatus.NON_WORKING_DAY, 0, 0, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.NOT_APPLICABLE);
    }

    @Test
    void rosteredAbsentIsNoShow() {
        DailySummary s = row("ROSTER", SummaryStatus.ABSENT, 0, 0, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.NO_SHOW);
    }

    @Test
    void presentWithLateMinutesIsLate() {
        DailySummary s = row("ROSTER", SummaryStatus.PRESENT, 12, 0, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.LATE);
    }

    @Test
    void presentWithEarlyLeaveIsEarlyLeave() {
        DailySummary s = row("ROSTER", SummaryStatus.PRESENT, 0, 20, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.EARLY_LEAVE);
    }

    @Test
    void presentWithOvertimeIsUnplannedOt() {
        DailySummary s = row("ROSTER", SummaryStatus.PRESENT, 0, 0, 45);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.UNPLANNED_OT);
    }

    @Test
    void presentWithNothingFlaggedIsOnTime() {
        DailySummary s = row("ROSTER", SummaryStatus.PRESENT, 0, 0, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.ON_TIME);
    }

    @Test
    void latePriorityOverOvertime() {
        // Showed up 5 min late AND stayed 30 min late. LATE is the more
        // actionable signal (lateness is involuntary; OT after a late
        // start is often "make-up time" rather than a planning miss).
        DailySummary s = row("ROSTER", SummaryStatus.PRESENT, 5, 0, 30);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.LATE);
    }

    @Test
    void latePriorityOverEarlyLeave() {
        // Can't physically be both late AND leave early on a single shift,
        // but the categoriser must still report a deterministic answer if
        // a manual correction sets both fields. LATE wins.
        DailySummary s = row("ROSTER", SummaryStatus.PRESENT, 5, 5, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.LATE);
    }

    @Test
    void earlyLeavePriorityOverOvertime() {
        // Defensive — both early and OT shouldn't co-exist, but EARLY_LEAVE
        // is the more managerially actionable bucket.
        DailySummary s = row("ROSTER", SummaryStatus.PRESENT, 0, 10, 15);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.EARLY_LEAVE);
    }

    @Test
    void partialIsNotApplicable() {
        // Only IN or only OUT — manual correction is usually pending. Don't
        // pollute the variance dashboard until a human decides.
        DailySummary s = row("ROSTER", SummaryStatus.PARTIAL, 0, 0, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.NOT_APPLICABLE);
    }

    @Test
    void nullSummaryIsNotApplicable() {
        assertThat(VarianceCategory.of(null)).isEqualTo(VarianceCategory.NOT_APPLICABLE);
    }

    @Test
    void nullStatusIsNotApplicable() {
        // Defensive — a DB row missing its status enum.
        DailySummary s = row("ROSTER", null, 0, 0, 0);
        assertThat(VarianceCategory.of(s)).isEqualTo(VarianceCategory.NOT_APPLICABLE);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static DailySummary row(String source, SummaryStatus status,
                                      int lateMin, int earlyMin, int otMin) {
        DailySummary s = new DailySummary();
        s.setId(UUID.randomUUID());
        s.setEmployeeId(UUID.randomUUID());
        s.setWorkDate(LocalDate.of(2026, 6, 1));
        s.setSource(source);
        s.setStatus(status);
        s.setLateMinutes(lateMin);
        s.setEarlyMinutes(earlyMin);
        s.setOvertimeMinutes(otMin);
        return s;
    }
}
