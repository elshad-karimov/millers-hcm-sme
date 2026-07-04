package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M131 — pure-static math for the team time-off calendar.
 *
 * <p>Given a list of leave intervals + a team list + a month window,
 * answers the two questions the SPA needs:
 * <ol>
 *   <li>"how many people are out on day X?" — {@link #dailyCounts}</li>
 *   <li>"which days are concurrent-absence hot-spots?" — {@link #flagDays}</li>
 * </ol>
 *
 * <p>Mockito-free so it's pinned by plain JUnit (Java 25 class-file
 * v69 isn't supported by Byte Buddy).
 */
public final class LeaveOverlapAnalyzer {

    /** Minimum sensible window size before "% off" becomes meaningful. */
    public static final int MIN_TEAM_FOR_THRESHOLD = 2;

    private LeaveOverlapAnalyzer() {}

    /**
     * Pure-data view of a leave interval — only what the analyzer needs.
     * APPROVED + PENDING both count (PENDING shaded differently in SPA).
     */
    public record LeaveInterval(
            UUID employeeId,
            LocalDate startDate,
            LocalDate endDate,
            /** {@code true} when status == APPROVED, false for PENDING. */
            boolean approved) {}

    /**
     * Every date between {@code from} and {@code to} inclusive, in order.
     * Returns empty if {@code to} is before {@code from}.
     */
    public static List<LocalDate> expandRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) return List.of();
        List<LocalDate> out = new ArrayList<>();
        LocalDate cur = from;
        while (!cur.isAfter(to)) {
            out.add(cur);
            cur = cur.plusDays(1);
        }
        return out;
    }

    /**
     * For each day in {@code [windowStart, windowEnd]}, how many DISTINCT
     * employees are out for any part of that day (approved + pending
     * merged together).
     *
     * <p>Two leaves from the same employee that overlap on the same day
     * count once. The returned map is iteration-ordered by date for
     * deterministic SPA rendering.
     */
    public static Map<LocalDate, Integer> dailyCounts(Collection<LeaveInterval> intervals,
                                                       LocalDate windowStart,
                                                       LocalDate windowEnd) {
        Map<LocalDate, Integer> out = new LinkedHashMap<>();
        for (LocalDate d : expandRange(windowStart, windowEnd)) out.put(d, 0);
        if (intervals == null || intervals.isEmpty() || out.isEmpty()) return out;
        // Track which employee-days we've already counted to de-dup.
        Map<LocalDate, java.util.Set<UUID>> seen = new HashMap<>();
        for (LeaveInterval li : intervals) {
            if (li.startDate() == null || li.endDate() == null) continue;
            LocalDate sliceStart = li.startDate().isBefore(windowStart) ? windowStart : li.startDate();
            LocalDate sliceEnd   = li.endDate().isAfter(windowEnd)      ? windowEnd   : li.endDate();
            if (sliceEnd.isBefore(sliceStart)) continue;
            LocalDate cur = sliceStart;
            while (!cur.isAfter(sliceEnd)) {
                var set = seen.computeIfAbsent(cur, k -> new java.util.HashSet<>());
                if (set.add(li.employeeId())) {
                    out.merge(cur, 1, Integer::sum);
                }
                cur = cur.plusDays(1);
            }
        }
        return out;
    }

    /**
     * Days within {@code dailyCounts} where the % of {@code teamSize} out
     * meets or exceeds {@code thresholdPercent}. Below
     * {@link #MIN_TEAM_FOR_THRESHOLD}, returns empty (a 1-person "team"
     * is always 100% off when someone's on leave — not useful signal).
     *
     * <p>{@code thresholdPercent} of 0/null disables flagging.
     */
    public static List<LocalDate> flagDays(Map<LocalDate, Integer> dailyCounts,
                                            int teamSize,
                                            BigDecimal thresholdPercent) {
        if (dailyCounts == null || dailyCounts.isEmpty()) return List.of();
        if (teamSize < MIN_TEAM_FOR_THRESHOLD) return List.of();
        if (thresholdPercent == null || thresholdPercent.signum() <= 0) return List.of();
        BigDecimal sizeBd = BigDecimal.valueOf(teamSize);
        List<LocalDate> flagged = new ArrayList<>();
        for (var e : dailyCounts.entrySet()) {
            BigDecimal pct = BigDecimal.valueOf(e.getValue())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(sizeBd, 2, RoundingMode.HALF_UP);
            if (pct.compareTo(thresholdPercent) >= 0) flagged.add(e.getKey());
        }
        return flagged;
    }

    /**
     * Convenience: percent-off for one day, scale 2 HALF_UP.
     * Returns 0 when team size &lt; 1.
     */
    public static BigDecimal percentOff(int outCount, int teamSize) {
        if (teamSize < 1) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(outCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(teamSize), 2, RoundingMode.HALF_UP);
    }
}
