package az.millers.hcm.timesheet.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.timesheet.domain.DayQuantity;
import az.millers.hcm.timesheet.domain.TimeCategory;
import az.millers.hcm.timesheet.domain.TimesheetDay;
import az.millers.hcm.timesheet.domain.WorkType;

/**
 * Decides whether a month may be submitted, and what the approver should be
 * told about it.
 *
 * <p>The distinction that matters: a <em>blocking</em> finding is data that
 * cannot be true (negative hours, 28 hours in a day, leave and a full day's
 * work at once) and refuses submission. A <em>warning</em> is data that is
 * merely unusual (hours above what attendance saw, weekend work, no biometric
 * record) — offshore crews legitimately produce all three, so blocking them
 * would make the system unusable for exactly the population it exists for.
 * Warnings travel with the submission to the approver instead.
 */
@Component
public class TimesheetValidator {

    private static final String MAX_DAILY_HOURS_KEY = "timesheet.validation.max-daily-hours";
    /**
     * 12 h, per the rule printed on the paper timesheet ("The daily work hours
     * shouldn't exceed 12 hours"). Was 16; tenants that genuinely allow longer
     * days override the setting rather than every crew silently exceeding it.
     */
    private static final String DEFAULT_MAX_DAILY_HOURS = "12";
    /** Onshore OT ceiling across any two consecutive days (paper form, 5/2 crews). */
    private static final String MAX_OT_TWO_DAYS_KEY = "timesheet.validation.max-overtime-two-consecutive-days";
    private static final String DEFAULT_MAX_OT_TWO_DAYS = "4";
    /**
     * V322: the prototype marks Project / Cost Code required. Off by default —
     * see the migration header: turning it on before the project list is loaded
     * would make every timesheet unsubmittable.
     */
    private static final String REQUIRE_PROJECT_KEY = "timesheet.validation.require-project";
    private static final String MAX_MONTHLY_OT_KEY = "timesheet.validation.max-monthly-overtime";
    private static final String DEFAULT_MAX_MONTHLY_OT = "60";
    private static final String VARIANCE_TOLERANCE_KEY = "timesheet.validation.variance-tolerance-hours";
    private static final String DEFAULT_VARIANCE_TOLERANCE = "0.5";

    private final SettingService settings;

    public TimesheetValidator(SettingService settings) {
        this.settings = settings;
    }

    /** One finding about a day or the month as a whole. */
    public record Finding(String code, String severity, LocalDate date, String message) {

        public static Finding blocking(String code, LocalDate date, String message) {
            return new Finding(code, "BLOCKING", date, message);
        }

        public static Finding warning(String code, LocalDate date, String message) {
            return new Finding(code, "WARNING", date, message);
        }

        public boolean isBlocking() {
            return "BLOCKING".equals(severity);
        }
    }

    /**
     * Validate one day in isolation. Called on every save so the employee sees a
     * problem the moment they cause it, not at submission time.
     *
     * @param categories   the active catalog, by code
     * @param quantities   what is being stored for the day
     * @param isHoliday    whether the calendar flags the date
     * @param isWorkingDay whether the employee's schedule expects work
     */
    public List<Finding> validateDay(TimesheetDay day,
                                     Map<String, TimeCategory> categories,
                                     List<DayQuantity> quantities,
                                     boolean isHoliday,
                                     boolean isWorkingDay) {
        List<Finding> out = new ArrayList<>();
        LocalDate date = day.getWorkDate();
        WorkType type = day.getWorkType();

        BigDecimal totalHours = BigDecimal.ZERO;
        Map<String, BigDecimal> byCode = new HashMap<>();

        for (DayQuantity q : quantities) {
            TimeCategory cat = categories.get(q.getCategoryCode());
            if (cat == null) {
                out.add(Finding.blocking("UNKNOWN_CATEGORY", date,
                        "Unknown time category: " + q.getCategoryCode()));
                continue;
            }
            BigDecimal value = q.getQuantity() == null ? BigDecimal.ZERO : q.getQuantity();
            byCode.merge(cat.getCode(), value, BigDecimal::add);

            if (value.signum() < 0) {
                out.add(Finding.blocking("NEGATIVE_QUANTITY", date,
                        cat.getName() + " cannot be negative."));
            }
            if (value.compareTo(cat.getMaxPerDay()) > 0) {
                out.add(Finding.blocking("ABOVE_DAILY_MAX", date,
                        cat.getName() + " cannot exceed " + cat.getMaxPerDay()
                                + " " + cat.getUnit().toLowerCase() + " in one day."));
            }
            if (!cat.appliesTo(type)) {
                out.add(Finding.blocking("CATEGORY_WORK_TYPE_MISMATCH", date,
                        cat.getName() + " cannot be recorded against work type "
                                + (type == null ? "(none)" : type.name()) + "."));
            }
            // Holiday-premium categories only exist on an actual holiday.
            if (!isHoliday && (DayQuantityDeriver.OFFSHORE_HOLIDAY.equals(cat.getCode())
                    || DayQuantityDeriver.QUAYSIDE_HOLIDAY.equals(cat.getCode()))
                    && value.signum() > 0) {
                out.add(Finding.blocking("HOLIDAY_ON_NON_HOLIDAY", date,
                        date + " is not a public holiday, so " + cat.getName()
                                + " cannot be recorded."));
            }
            // Only base work categories count toward the day's hour total.
            // A premium category (holiday rota, nightshift, leave) re-classifies
            // hours that are ALREADY counted — 12 offshore hours on a holiday is
            // twelve hours, not twenty-four — so adding it here would trip the
            // daily ceiling on a perfectly ordinary rota day.
            //
            // Tested against the CODE, not the derived flag: V321 handed those
            // six categories to the employee to type, and keying this off
            // isDerived() would have started double-counting every nightshift
            // hour the day that shipped.
            if (cat.isHours() && !DayQuantityDeriver.isCrossCheckable(cat.getCode())) {
                totalHours = totalHours.add(value);
            }
        }

        // Night hours are a subset of the hours worked, never an addition to them.
        BigDecimal worked = nonNull(byCode.get("OFFSHORE_HOURS"))
                .add(nonNull(byCode.get("QUAYSIDE_HOURS")))
                .add(nonNull(byCode.get("ONSHORE_HOURS")));
        BigDecimal night = nonNull(byCode.get(DayQuantityDeriver.OFFSHORE_NIGHT))
                .add(nonNull(byCode.get(DayQuantityDeriver.QUAYSIDE_NIGHT)));
        if (night.compareTo(worked) > 0) {
            out.add(Finding.blocking("NIGHT_EXCEEDS_WORKED", date,
                    "Night hours (" + night + ") cannot exceed hours worked (" + worked + ")."));
        }

        // Leave and a full day's work cannot both be true.
        BigDecimal leaveHours = nonNull(byCode.get(DayQuantityDeriver.VACATION))
                .add(nonNull(byCode.get(DayQuantityDeriver.SICK)));
        if (leaveHours.signum() > 0 && worked.signum() > 0) {
            out.add(Finding.blocking("LEAVE_AND_WORK", date,
                    "This day is covered by approved leave, so working hours cannot "
                            + "also be recorded. Cancel the leave request first."));
        }

        BigDecimal maxDaily = decimalSetting(MAX_DAILY_HOURS_KEY, DEFAULT_MAX_DAILY_HOURS);
        if (totalHours.compareTo(maxDaily) > 0) {
            out.add(Finding.blocking("ABOVE_MAX_DAILY_HOURS", date,
                    "Total hours for " + date + " (" + totalHours + ") exceed the "
                            + maxDaily + "-hour daily maximum."));
        }

        // V322: cost attribution. Only demanded on a day that actually recorded
        // work — a rest day has nothing to charge to a project.
        if (booleanSetting(REQUIRE_PROJECT_KEY)
                && totalHours.signum() > 0
                && day.getProjectId() == null
                && (day.getTaskCode() == null || day.getTaskCode().isBlank())) {
            out.add(Finding.blocking("MISSING_PROJECT", date,
                    "Project / Cost Code is required for " + date + "."));
        }

        // ---- warnings ----
        if (day.getAttendanceVarianceHours() != null) {
            BigDecimal tolerance = decimalSetting(VARIANCE_TOLERANCE_KEY, DEFAULT_VARIANCE_TOLERANCE);
            if (day.getAttendanceVarianceHours().abs().compareTo(tolerance) > 0) {
                out.add(Finding.warning("ATTENDANCE_VARIANCE", date,
                        "Entered hours differ from attendance by "
                                + day.getAttendanceVarianceHours() + " h."));
            }
        }
        if (totalHours.signum() > 0 && !isWorkingDay && !isHoliday
                && (date.getDayOfWeek() == DayOfWeek.SATURDAY
                    || date.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            out.add(Finding.warning("WEEKEND_WORK", date, "Hours recorded on a weekend."));
        }
        return out;
    }

    /**
     * Validate the month as a whole at submission time — the checks that can
     * only be made once every day is present.
     */
    public List<Finding> validateMonth(List<TimesheetDay> days,
                                       Map<String, BigDecimal> monthTotals,
                                       Set<LocalDate> requiredWorkingDays,
                                       Set<LocalDate> holidayDates) {
        List<Finding> out = new ArrayList<>();

        Set<LocalDate> entered = new java.util.HashSet<>();
        for (TimesheetDay d : days) {
            if (d.getWorkType() != null) entered.add(d.getWorkDate());
        }
        for (LocalDate required : requiredWorkingDays) {
            if (holidayDates.contains(required)) continue;
            if (!entered.contains(required)) {
                out.add(Finding.blocking("MISSING_REQUIRED_DAY", required,
                        "No entry for working day " + required + "."));
            }
        }

        BigDecimal overtime = nonNull(monthTotals.get("ONSHORE_OVERTIME_HOURS"));
        BigDecimal maxOt = decimalSetting(MAX_MONTHLY_OT_KEY, DEFAULT_MAX_MONTHLY_OT);
        if (overtime.compareTo(maxOt) > 0) {
            out.add(Finding.warning("OVERTIME_ABOVE_THRESHOLD", null,
                    "Overtime for the month (" + overtime + " h) exceeds the usual "
                            + maxOt + "-hour threshold."));
        }

        out.addAll(overtimeAcrossTwoDays(days));
        return out;
    }

    /**
     * Art. 99 as printed on the paper form: "Employees on 5/2 shall not work
     * overtime in excess of 4 hours during two consecutive working days."
     *
     * <p>Blocking, and reported on the SECOND day of the offending pair — that
     * is the entry the employee has to change, and anchoring it there stops one
     * long day from flagging its innocent neighbour.
     *
     * <p>Consecutive means calendar-adjacent: a Friday/Monday pair is not one.
     */
    private List<Finding> overtimeAcrossTwoDays(List<TimesheetDay> days) {
        BigDecimal cap = decimalSetting(MAX_OT_TWO_DAYS_KEY, DEFAULT_MAX_OT_TWO_DAYS);
        List<TimesheetDay> ordered = days.stream()
                .filter(d -> d.getWorkDate() != null)
                .sorted(java.util.Comparator.comparing(TimesheetDay::getWorkDate))
                .toList();

        List<Finding> out = new ArrayList<>();
        for (int i = 1; i < ordered.size(); i++) {
            TimesheetDay previous = ordered.get(i - 1);
            TimesheetDay current = ordered.get(i);
            if (!previous.getWorkDate().plusDays(1).equals(current.getWorkDate())) {
                continue;
            }
            BigDecimal pair = nonNull(previous.getOvertimeHours()).add(nonNull(current.getOvertimeHours()));
            if (pair.compareTo(cap) > 0) {
                out.add(Finding.blocking("OVERTIME_TWO_DAY_CAP", current.getWorkDate(),
                        "Overtime over " + previous.getWorkDate() + " and " + current.getWorkDate()
                                + " totals " + pair.stripTrailingZeros().toPlainString()
                                + " h, above the " + cap.stripTrailingZeros().toPlainString()
                                + "-hour limit for two consecutive days."));
            }
        }
        return out;
    }

    private BigDecimal nonNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private boolean booleanSetting(String key) {
        try {
            return "true".equalsIgnoreCase(settings.get(key, "false").trim());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private BigDecimal decimalSetting(String key, String fallback) {
        try {
            return new BigDecimal(settings.get(key, fallback).trim());
        } catch (RuntimeException e) {
            return new BigDecimal(fallback);
        }
    }
}
