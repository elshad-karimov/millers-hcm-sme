package az.millers.hcm.timesheet.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.timesheet.domain.DayQuantity;
import az.millers.hcm.timesheet.domain.TimeCategory;
import az.millers.hcm.timesheet.domain.TimesheetDay;
import az.millers.hcm.timesheet.domain.WorkType;
import az.millers.hcm.timesheet.service.TimesheetValidator.Finding;

/**
 * Pins the blocking/warning split.
 *
 * <p>The split is the whole point: offshore crews legitimately produce hours
 * with no biometric record, weekend work and attendance variance, so those must
 * warn and travel to the approver rather than refuse the submission. Data that
 * cannot be true — negative hours, leave and a full day's work at once, night
 * hours exceeding hours worked — must block.
 */
class TimesheetValidatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);

    private TimesheetValidator validator;
    private Map<String, TimeCategory> catalog;

    @BeforeEach
    void setUp() {
        validator = new TimesheetValidator(new SettingService(null, null, null) {
            @Override
            public String get(String key, String defaultValue) {
                return defaultValue;
            }
        });
        catalog = new LinkedHashMap<>();
        put("OFFSHORE_HOURS", "Offshore Hours", "HOURS", "OFFSHORE", 24, false);
        put("ONSHORE_HOURS", "Onshore Working Hours", "HOURS", "ONSHORE", 24, false);
        put("ONSHORE_OVERTIME_HOURS", "Onshore Overtime Hours", "HOURS", "ONSHORE", 12, false);
        put("QUAYSIDE_HOURS", "Quayside Hours", "HOURS", "QUAYSIDE", 24, false);
        put("MEAL_ALLOWANCE_DAYS", "Meal Allowance", "DAYS", "ONSHORE", 1, false);
        put("OFFSHORE_NIGHT_HOURS", "Offshore Nightshift Hours", "HOURS", "OFFSHORE", 12, true);
        put("OFFSHORE_HOLIDAY_HOURS", "Offshore Public Holiday Hours", "HOURS", "OFFSHORE", 24, true);
        put("VACATION_HOURS", "Vacation Hours", "HOURS", "LEAVE", 24, true);
    }

    private void put(String code, String name, String unit, String appliesTo,
                     int maxPerDay, boolean derived) {
        TimeCategory c = new TimeCategory();
        c.setCode(code);
        c.setName(name);
        c.setUnit(unit);
        c.setAppliesTo(appliesTo);
        c.setMaxPerDay(BigDecimal.valueOf(maxPerDay));
        c.setDerived(derived);
        catalog.put(code, c);
    }

    private TimesheetDay day(WorkType type) {
        TimesheetDay d = new TimesheetDay();
        d.setId(UUID.randomUUID());
        d.setWorkDate(MONDAY);
        d.setWorkType(type);
        return d;
    }

    private DayQuantity qty(String code, String value) {
        return new DayQuantity(UUID.randomUUID(), code, new BigDecimal(value));
    }

    private List<String> codes(List<Finding> findings, boolean blocking) {
        return findings.stream()
                .filter(f -> f.isBlocking() == blocking)
                .map(Finding::code)
                .toList();
    }

    // ---- blocking ----

    @Test
    void negativeHoursBlock() {
        List<Finding> f = validator.validateDay(day(WorkType.OFFSHORE), catalog,
                List.of(qty("OFFSHORE_HOURS", "-2")), false, true);

        assertThat(codes(f, true)).contains("NEGATIVE_QUANTITY");
    }

    @Test
    void moreThanTheDailyMaximumBlocks() {
        List<Finding> f = validator.validateDay(day(WorkType.OFFSHORE), catalog,
                List.of(qty("OFFSHORE_HOURS", "20")), false, true);

        assertThat(codes(f, true)).contains("ABOVE_MAX_DAILY_HOURS");
    }

    @Test
    void aCategoryEnteredAgainstTheWrongWorkTypeBlocks() {
        // Quayside hours claimed on an offshore day.
        List<Finding> f = validator.validateDay(day(WorkType.OFFSHORE), catalog,
                List.of(qty("QUAYSIDE_HOURS", "8")), false, true);

        assertThat(codes(f, true)).contains("CATEGORY_WORK_TYPE_MISMATCH");
    }

    @Test
    void holidayPremiumOnANonHolidayBlocks() {
        List<Finding> f = validator.validateDay(day(WorkType.OFFSHORE), catalog,
                List.of(qty("OFFSHORE_HOURS", "12"), qty("OFFSHORE_HOLIDAY_HOURS", "12")),
                false, true);

        assertThat(codes(f, true)).contains("HOLIDAY_ON_NON_HOLIDAY");
    }

    @Test
    void holidayPremiumOnARealHolidayIsFine() {
        List<Finding> f = validator.validateDay(day(WorkType.OFFSHORE), catalog,
                List.of(qty("OFFSHORE_HOURS", "12"), qty("OFFSHORE_HOLIDAY_HOURS", "12")),
                true, true);

        assertThat(codes(f, true)).isEmpty();
    }

    @Test
    void nightHoursAboveHoursWorkedBlock() {
        List<Finding> f = validator.validateDay(day(WorkType.OFFSHORE), catalog,
                List.of(qty("OFFSHORE_HOURS", "4"), qty("OFFSHORE_NIGHT_HOURS", "8")),
                false, true);

        assertThat(codes(f, true)).contains("NIGHT_EXCEEDS_WORKED");
    }

    @Test
    void leaveAndAFullDaysWorkCannotBothBeTrue() {
        List<Finding> f = validator.validateDay(day(WorkType.OFFSHORE), catalog,
                List.of(qty("OFFSHORE_HOURS", "12"), qty("VACATION_HOURS", "8")),
                false, true);

        assertThat(codes(f, true)).contains("LEAVE_AND_WORK");
    }

    @Test
    void moreThanOneAllowanceDayBlocks() {
        List<Finding> f = validator.validateDay(day(WorkType.ONSHORE), catalog,
                List.of(qty("ONSHORE_HOURS", "8"), qty("MEAL_ALLOWANCE_DAYS", "2")),
                false, true);

        assertThat(codes(f, true)).contains("ABOVE_DAILY_MAX");
    }

    @Test
    void allowanceDaysDoNotCountTowardTheDailyHourCeiling() {
        List<Finding> f = validator.validateDay(day(WorkType.ONSHORE), catalog,
                List.of(qty("ONSHORE_HOURS", "8"), qty("MEAL_ALLOWANCE_DAYS", "1")),
                false, true);

        assertThat(codes(f, true)).isEmpty();
    }

    // ---- warnings, not blocks ----

    @Test
    void attendanceVarianceWarnsButDoesNotBlock() {
        TimesheetDay d = day(WorkType.OFFSHORE);
        d.setAttendanceVarianceHours(new BigDecimal("4.00"));   // no device offshore

        List<Finding> f = validator.validateDay(d, catalog,
                List.of(qty("OFFSHORE_HOURS", "12")), false, true);

        assertThat(codes(f, true)).isEmpty();
        assertThat(codes(f, false)).contains("ATTENDANCE_VARIANCE");
    }

    @Test
    void varianceWithinToleranceIsNotEvenAWarning() {
        TimesheetDay d = day(WorkType.OFFSHORE);
        d.setAttendanceVarianceHours(new BigDecimal("0.15"));

        List<Finding> f = validator.validateDay(d, catalog,
                List.of(qty("OFFSHORE_HOURS", "12")), false, true);

        assertThat(f).isEmpty();
    }

    @Test
    void weekendWorkWarnsButDoesNotBlock() {
        TimesheetDay d = day(WorkType.OFFSHORE);
        d.setWorkDate(LocalDate.of(2026, 1, 10));               // Saturday

        List<Finding> f = validator.validateDay(d, catalog,
                List.of(qty("OFFSHORE_HOURS", "12")), false, false);

        assertThat(codes(f, true)).isEmpty();
        assertThat(codes(f, false)).contains("WEEKEND_WORK");
    }

    // ---- month level ----

    @Test
    void aMissingRequiredWorkingDayBlocksSubmission() {
        TimesheetDay entered = day(WorkType.ONSHORE);

        List<Finding> f = validator.validateMonth(List.of(entered), Map.of(),
                Set.of(MONDAY, MONDAY.plusDays(1)), Set.of());

        assertThat(codes(f, true)).containsExactly("MISSING_REQUIRED_DAY");
        assertThat(f.get(0).date()).isEqualTo(MONDAY.plusDays(1));
    }

    @Test
    void aMissingDayThatIsAPublicHolidayIsNotMissing() {
        TimesheetDay entered = day(WorkType.ONSHORE);

        List<Finding> f = validator.validateMonth(List.of(entered), Map.of(),
                Set.of(MONDAY, MONDAY.plusDays(1)), Set.of(MONDAY.plusDays(1)));

        assertThat(codes(f, true)).isEmpty();
    }

    @Test
    void overtimeAboveTheMonthlyThresholdOnlyWarns() {
        List<Finding> f = validator.validateMonth(List.of(),
                Map.of("ONSHORE_OVERTIME_HOURS", new BigDecimal("80")), Set.of(), Set.of());

        assertThat(codes(f, true)).isEmpty();
        assertThat(codes(f, false)).contains("OVERTIME_ABOVE_THRESHOLD");
    }
}
