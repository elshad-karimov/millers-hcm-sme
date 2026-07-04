package az.millers.hcm.attendance.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Pure-math helpers for the attendance engine (M112).
 *
 * <p>Pre-M112 every late/early/overtime calculation lived inline in
 * {@link AttendanceEngine#computeFor}. Splitting it out lets us:
 * <ul>
 *   <li>Pin the math with unit tests that don't need a Spring context.</li>
 *   <li>Cleanly express the cross-midnight case (a Night shift starting at
 *       22:00 with the clock-out the next morning) without doubling-up the
 *       work-date logic.</li>
 *   <li>Share the same math between the legacy WorkSchedule path and the
 *       new RosterEntry path.</li>
 * </ul>
 */
public final class ScheduleMath {

    private ScheduleMath() {}

    /**
     * The scheduled work window on a specific calendar date.
     *
     * <p>{@code startTime}/{@code endTime} both pin to {@code workDate}'s
     * day for a daytime shift; for a shift that crosses midnight,
     * {@code endTime} points to the next day. Caller-supplied
     * {@code zone} controls how local times convert to instants — same zone
     * the engine uses for event lookups.
     */
    public record Window(OffsetDateTime start, OffsetDateTime end) {

        /** Was the work span at least one minute long? */
        public boolean isPositive() { return start.isBefore(end); }
    }

    /** Aggregated late/early/worked/overtime numbers, all in minutes. */
    public record Metrics(int workedMinutes,
                          int lateMinutes,
                          int earlyMinutes,
                          int overtimeMinutes) {
    }

    /**
     * Builds the scheduled work window for {@code workDate}. When
     * {@code crossesMidnight} is true, {@code end} resolves to {@code workDate
     * + 1} at {@code endTime}.
     */
    public static Window window(LocalDate workDate,
                                 LocalTime startTime,
                                 LocalTime endTime,
                                 boolean crossesMidnight,
                                 ZoneId zone) {
        if (workDate == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("workDate, startTime, endTime are required");
        }
        if (zone == null) zone = ZoneId.systemDefault();
        OffsetDateTime start = workDate.atTime(startTime).atZone(zone).toOffsetDateTime();
        LocalDate endDate = crossesMidnight ? workDate.plusDays(1) : workDate;
        OffsetDateTime end = endDate.atTime(endTime).atZone(zone).toOffsetDateTime();
        return new Window(start, end);
    }

    /**
     * The half-open instant window the engine uses to fetch
     * {@link az.millers.hcm.attendance.domain.AttendanceEvent} rows
     * relevant to this scheduled span.
     *
     * <p>{@code slack} extends the window on both sides so an employee who
     * clocks in 15 minutes early or out an hour late still falls inside the
     * lookup. For night shifts where the shift end is on the next calendar
     * day this is the only way we'd catch the morning clock-out.
     */
    public static Window eventFetchWindow(Window scheduled, Duration slack) {
        if (slack == null || slack.isNegative()) slack = Duration.ZERO;
        return new Window(
                scheduled.start().minus(slack),
                scheduled.end().plus(slack));
    }

    /**
     * Compute the late / early / overtime / worked-minutes summary for a
     * shift given the first IN, last OUT, break, and grace allowance.
     *
     * <p>Behaviour:
     * <ul>
     *   <li>{@code lateMinutes} = max(0, firstIn - schStart - graceMin)</li>
     *   <li>{@code earlyMinutes} = max(0, schEnd - lastOut)</li>
     *   <li>{@code overtimeMinutes} = max(0, lastOut - schEnd)</li>
     *   <li>{@code workedMinutes} = max(0, (lastOut - firstIn) - breakMinutes)</li>
     * </ul>
     */
    public static Metrics metrics(OffsetDateTime scheduledStart,
                                   OffsetDateTime scheduledEnd,
                                   OffsetDateTime firstIn,
                                   OffsetDateTime lastOut,
                                   int breakMinutes,
                                   int graceMinutes) {
        if (firstIn == null || lastOut == null) {
            return new Metrics(0, 0, 0, 0);
        }
        int total = (int) Duration.between(firstIn, lastOut).toMinutes();
        int worked = Math.max(0, total - Math.max(0, breakMinutes));

        int late;
        if (scheduledStart == null) {
            late = 0;
        } else {
            long raw = Duration.between(scheduledStart, firstIn).toMinutes();
            late = (int) Math.max(0, raw - Math.max(0, graceMinutes));
        }

        int early;
        int overtime;
        if (scheduledEnd == null) {
            early = 0;
            overtime = 0;
        } else {
            long earlyRaw = Duration.between(lastOut, scheduledEnd).toMinutes();
            early = (int) Math.max(0, earlyRaw);
            long otRaw = Duration.between(scheduledEnd, lastOut).toMinutes();
            overtime = (int) Math.max(0, otRaw);
        }
        return new Metrics(worked, late, early, overtime);
    }
}
