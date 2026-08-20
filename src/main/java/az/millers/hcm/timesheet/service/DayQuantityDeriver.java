package az.millers.hcm.timesheet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import az.millers.hcm.attendance.domain.ScheduleAssignment;
import az.millers.hcm.attendance.domain.WorkSchedule;
import az.millers.hcm.attendance.repo.ScheduleAssignmentRepository;
import az.millers.hcm.attendance.repo.WorkScheduleRepository;
import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.timesheet.domain.DayQuantity;
import az.millers.hcm.timesheet.domain.TimeCategory;
import az.millers.hcm.timesheet.domain.WorkType;

/**
 * Turns what the employee said into the quantities payroll will need.
 *
 * <p>The employee says "12 hours, offshore". If that date is a public holiday
 * the system — not the employee — produces {@code OFFSHORE_HOLIDAY_HOURS}; if
 * the scheduled shift runs into the night window it produces
 * {@code OFFSHORE_NIGHT_HOURS}. Employees never have to know payroll's field
 * names, and a derived quantity can never drift from the calendar or roster
 * that defines it, because it is recomputed on every save.
 */
@Component
public class DayQuantityDeriver {

    /** Tenant-configurable night window; default matches the common 22:00–06:00 rule. */
    private static final String NIGHT_START_KEY = "timesheet.night.window.start";
    private static final String NIGHT_END_KEY = "timesheet.night.window.end";
    private static final String DEFAULT_NIGHT_START = "22:00";
    private static final String DEFAULT_NIGHT_END = "06:00";

    /** Category codes this class owns. Employee-typed values for these are replaced. */
    static final String OFFSHORE_HOLIDAY = "OFFSHORE_HOLIDAY_HOURS";
    static final String QUAYSIDE_HOLIDAY = "QUAYSIDE_HOLIDAY_HOURS";
    static final String OFFSHORE_NIGHT = "OFFSHORE_NIGHT_HOURS";
    static final String QUAYSIDE_NIGHT = "QUAYSIDE_NIGHT_HOURS";
    static final String VACATION = "VACATION_HOURS";
    static final String SICK = "SICK_LEAVE_HOURS";

    static final Set<String> DERIVED_CODES = Set.of(
            OFFSHORE_HOLIDAY, QUAYSIDE_HOLIDAY, OFFSHORE_NIGHT, QUAYSIDE_NIGHT, VACATION, SICK);

    private final ScheduleAssignmentRepository assignments;
    private final WorkScheduleRepository schedules;
    private final SettingService settings;

    public DayQuantityDeriver(ScheduleAssignmentRepository assignments,
                              WorkScheduleRepository schedules,
                              SettingService settings) {
        this.assignments = assignments;
        this.schedules = schedules;
        this.settings = settings;
    }

    /**
     * Derived quantities for one day.
     *
     * @param employeeId  whose day it is
     * @param date        the calendar date
     * @param workType    what the employee selected
     * @param workedHours the hours the employee declared for that work type
     * @param isHoliday   whether the holiday calendar flags this date
     * @param leave       an approved leave request covering the date, or null
     */
    public List<DayQuantity> derive(UUID employeeId, LocalDate date, WorkType workType,
                                    BigDecimal workedHours, boolean isHoliday, LeaveRequest leave) {
        List<DayQuantity> out = new ArrayList<>();

        // Leave wins: an approved request, not a typed number, defines the day.
        if (leave != null && workType != null && workType.isLeave()) {
            String code = workType == WorkType.SICK ? SICK : VACATION;
            BigDecimal hours = leaveHoursFor(leave, date);
            out.add(quantity(code, hours, "LEAVE"));
            return out;
        }

        if (workType == null || workedHours == null || workedHours.signum() <= 0) {
            return out;
        }

        // Public-holiday rota — the employee entered plain hours on a date the
        // calendar marks as a holiday; the premium category follows from that.
        if (isHoliday) {
            if (workType == WorkType.OFFSHORE) {
                out.add(quantity(OFFSHORE_HOLIDAY, workedHours, "HOLIDAY_CALENDAR"));
            } else if (workType == WorkType.QUAYSIDE) {
                out.add(quantity(QUAYSIDE_HOLIDAY, workedHours, "HOLIDAY_CALENDAR"));
            }
        }

        // Night portion, from the scheduled shift rather than the employee's word.
        BigDecimal night = nightHours(employeeId, date, workedHours);
        if (night.signum() > 0) {
            if (workType == WorkType.OFFSHORE) {
                out.add(quantity(OFFSHORE_NIGHT, night, "SHIFT_SCHEDULE"));
            } else if (workType == WorkType.QUAYSIDE) {
                out.add(quantity(QUAYSIDE_NIGHT, night, "SHIFT_SCHEDULE"));
            }
        }
        return out;
    }

    /**
     * The part of the employee's scheduled working window that falls inside the
     * night window, capped at the hours actually declared.
     *
     * <p>Returns zero when the employee has no schedule — an offshore worker on
     * a rota with no roster row gets no derived night hours rather than a
     * guessed number, and the approver sees the gap.
     */
    BigDecimal nightHours(UUID employeeId, LocalDate date, BigDecimal workedHours) {
        Optional<WorkSchedule> schedule = assignments.findActiveOn(employeeId, date)
                .map(ScheduleAssignment::getScheduleId)
                .flatMap(schedules::findById);
        if (schedule.isEmpty()) return BigDecimal.ZERO;

        WorkSchedule s = schedule.get();
        if (s.getWorkStart() == null || s.getWorkEnd() == null) return BigDecimal.ZERO;

        LocalTime nightStart = parseTime(settings.get(NIGHT_START_KEY, DEFAULT_NIGHT_START), LocalTime.of(22, 0));
        LocalTime nightEnd = parseTime(settings.get(NIGHT_END_KEY, DEFAULT_NIGHT_END), LocalTime.of(6, 0));

        long minutes = overlapMinutes(s.getWorkStart(), s.getWorkEnd(), nightStart, nightEnd);
        if (minutes <= 0) return BigDecimal.ZERO;

        BigDecimal night = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        // Never claim more night hours than hours actually worked.
        return night.min(workedHours);
    }

    /**
     * Minutes of overlap between a work window and a night window, either of
     * which may wrap past midnight.
     *
     * <p>Both are unrolled onto a minute line, then the night window is tested
     * at yesterday's, today's and tomorrow's placement. Yesterday's matters:
     * an 04:00–12:00 shift overlaps the window that opened at 22:00 the
     * previous evening, and checking only today's would silently score that
     * morning rota as zero night hours. Occurrences are 24 h apart and the
     * window is never longer than that, so they cannot double-count.
     */
    static long overlapMinutes(LocalTime workStart, LocalTime workEnd,
                               LocalTime nightStart, LocalTime nightEnd) {
        final long day = 24 * 60L;

        long ws = workStart.toSecondOfDay() / 60L;
        long we = workEnd.toSecondOfDay() / 60L;
        if (we <= ws) we += day;                 // shift crosses midnight

        long ns = nightStart.toSecondOfDay() / 60L;
        long ne = nightEnd.toSecondOfDay() / 60L;
        if (ne <= ns) ne += day;                 // night window crosses midnight

        long total = 0;
        for (long offset : new long[] {-day, 0, day}) {
            total += Math.max(0, Math.min(we, ne + offset) - Math.max(ws, ns + offset));
        }
        return total;
    }

    /** Hours a leave request contributes to one date. */
    private BigDecimal leaveHoursFor(LeaveRequest leave, LocalDate date) {
        if (leave.getDurationHours() != null && leave.getDurationHours().signum() > 0
                && leave.getStartDate().equals(leave.getEndDate())) {
            return leave.getDurationHours();
        }
        // Full-day leave: charge the standard working day.
        long standard = Duration.ofHours(8).toHours();
        return BigDecimal.valueOf(standard);
    }

    private DayQuantity quantity(String code, BigDecimal value, String derivedFrom) {
        DayQuantity q = new DayQuantity(null, code, value);
        q.setDerivedFrom(derivedFrom);
        return q;
    }

    private LocalTime parseTime(String raw, LocalTime fallback) {
        try {
            return LocalTime.parse(raw.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** True when this category is system-owned and must not be employee-typed. */
    /**
     * Whether the system owns this category outright.
     *
     * <p>V321: the catalog flag alone decides. The six codes in
     * {@link #DERIVED_CODES} used to be forced derived here regardless of the
     * flag; they are now employee-typed (the paper timesheet types them), and
     * this class computes them as a <em>default and cross-check</em> instead of
     * as the owner. Keeping the hardcoded override would silently defeat V321.
     */
    public static boolean isDerived(TimeCategory category) {
        return category.isDerived();
    }

    /** Codes this class can compute — the employee's value wins, but we compare. */
    public static boolean isCrossCheckable(String code) {
        return DERIVED_CODES.contains(code);
    }
}
