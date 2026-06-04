package az.millers.hcm.attendance.service;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.SummaryStatus;

/**
 * Why a roster-driven daily summary diverged from the rostered shift
 * (M113). Computed by {@link #of(DailySummary)} — pure-static so the
 * categorisation can be pinned without Spring.
 *
 * <p>Only rows where {@code source == "ROSTER"} count toward variance —
 * legacy SCHEDULE-driven rows are reported as {@link #NOT_APPLICABLE}
 * so they don't pollute the dashboard. NONE-source rows (no schedule and
 * no roster) are also NOT_APPLICABLE — they're "the employee wasn't
 * supposed to work that day" and shouldn't count as misses.
 *
 * <p>Priority order: NO_SHOW &gt; LATE &gt; EARLY_LEAVE &gt; UNPLANNED_OT
 * &gt; ON_TIME. A row with both lateness and overtime counts as LATE —
 * lateness is the more actionable signal (someone arrived late and made
 * up the time, vs. someone arrived on time and stayed late by choice).
 */
public enum VarianceCategory {

    /** Rostered but completely absent (status = ABSENT). */
    NO_SHOW,
    /** Late beyond the grace period (lateMinutes > 0). */
    LATE,
    /** Left before the scheduled end (earlyMinutes > 0). */
    EARLY_LEAVE,
    /** Stayed past the scheduled end (overtimeMinutes > 0, no late/early). */
    UNPLANNED_OT,
    /** Showed up on time, left on time, no extras. */
    ON_TIME,
    /** Schedule-driven, NONE-source, or PARTIAL/non-working — excluded. */
    NOT_APPLICABLE;

    /**
     * Categorise a daily summary row. Pure math — no DB / no Spring.
     *
     * <p>Behaviour:
     * <ul>
     *   <li>Non-ROSTER source → NOT_APPLICABLE (we only judge rostered days).</li>
     *   <li>NON_WORKING_DAY → NOT_APPLICABLE.</li>
     *   <li>ABSENT → NO_SHOW.</li>
     *   <li>PARTIAL → NOT_APPLICABLE (manual correction usually pending).</li>
     *   <li>PRESENT + lateMinutes &gt; 0 → LATE.</li>
     *   <li>PRESENT + earlyMinutes &gt; 0 → EARLY_LEAVE.</li>
     *   <li>PRESENT + overtimeMinutes &gt; 0 → UNPLANNED_OT.</li>
     *   <li>PRESENT, none of the above → ON_TIME.</li>
     * </ul>
     */
    public static VarianceCategory of(DailySummary s) {
        if (s == null) return NOT_APPLICABLE;
        if (!"ROSTER".equals(s.getSource())) return NOT_APPLICABLE;
        SummaryStatus status = s.getStatus();
        if (status == null) return NOT_APPLICABLE;
        return switch (status) {
            case ABSENT -> NO_SHOW;
            case PRESENT -> {
                if (s.getLateMinutes() > 0) yield LATE;
                if (s.getEarlyMinutes() > 0) yield EARLY_LEAVE;
                if (s.getOvertimeMinutes() > 0) yield UNPLANNED_OT;
                yield ON_TIME;
            }
            default -> NOT_APPLICABLE;
        };
    }
}
