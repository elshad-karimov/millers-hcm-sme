package az.millers.hcm.timesheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import az.millers.hcm.attendance.domain.ScheduleAssignment;
import az.millers.hcm.attendance.domain.WorkSchedule;
import az.millers.hcm.attendance.repo.ScheduleAssignmentRepository;
import az.millers.hcm.attendance.repo.WorkScheduleRepository;
import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.timesheet.domain.DayQuantity;
import az.millers.hcm.timesheet.domain.WorkType;

/**
 * Pins the derivation rules that keep payroll field names out of the employee's
 * hands: the employee types "12 hours, offshore" and the system decides whether
 * that is holiday-rota time, night time, or neither.
 *
 * <p>Pure JUnit — no Spring, no DB.
 */
class DayQuantityDeriverTest {

    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final UUID SCHEDULE = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 1, 20);

    private ScheduleAssignmentRepository assignments;
    private WorkScheduleRepository schedules;
    private DayQuantityDeriver deriver;

    /**
     * Hand-rolled rather than a Mockito mock: SettingService is a concrete
     * class, and mocking those needs bytecode instrumentation that is not
     * available on every JDK this builds on. Returning the caller's default
     * exercises the shipped night window (22:00–06:00).
     */
    private static SettingService defaultSettings() {
        return new SettingService(null, null, null) {
            @Override
            public String get(String key, String defaultValue) {
                return defaultValue;
            }
        };
    }

    @BeforeEach
    void setUp() {
        assignments = mock(ScheduleAssignmentRepository.class);
        schedules = mock(WorkScheduleRepository.class);
        deriver = new DayQuantityDeriver(assignments, schedules, defaultSettings());
        noSchedule();
    }

    private void noSchedule() {
        when(assignments.findActiveOn(any(), any())).thenReturn(Optional.empty());
    }

    private void scheduleOf(LocalTime start, LocalTime end) {
        ScheduleAssignment a = new ScheduleAssignment();
        a.setScheduleId(SCHEDULE);
        WorkSchedule s = new WorkSchedule();
        s.setWorkStart(start);
        s.setWorkEnd(end);
        when(assignments.findActiveOn(eq(EMPLOYEE), any())).thenReturn(Optional.of(a));
        when(schedules.findById(SCHEDULE)).thenReturn(Optional.of(s));
    }

    // ---- holiday rota ----

    @Test
    void offshoreHoursOnAPublicHolidayBecomeHolidayRotaHours() {
        List<DayQuantity> out = deriver.derive(EMPLOYEE, DATE, WorkType.OFFSHORE,
                new BigDecimal("12.00"), true, null);

        assertThat(out).anySatisfy(q -> {
            assertThat(q.getCategoryCode()).isEqualTo("OFFSHORE_HOLIDAY_HOURS");
            assertThat(q.getQuantity()).isEqualByComparingTo("12.00");
            assertThat(q.getDerivedFrom()).isEqualTo("HOLIDAY_CALENDAR");
        });
    }

    @Test
    void quaysideHoursOnAPublicHolidayBecomeQuaysideHolidayHours() {
        List<DayQuantity> out = deriver.derive(EMPLOYEE, DATE, WorkType.QUAYSIDE,
                new BigDecimal("12.00"), true, null);

        assertThat(out).extracting(DayQuantity::getCategoryCode)
                .contains("QUAYSIDE_HOLIDAY_HOURS");
    }

    @Test
    void ordinaryDayProducesNoHolidayCategory() {
        List<DayQuantity> out = deriver.derive(EMPLOYEE, DATE, WorkType.OFFSHORE,
                new BigDecimal("12.00"), false, null);

        assertThat(out).extracting(DayQuantity::getCategoryCode)
                .doesNotContain("OFFSHORE_HOLIDAY_HOURS");
    }

    @Test
    void onshoreWorkOnAHolidayGetsNoOffshoreHolidayCategory() {
        List<DayQuantity> out = deriver.derive(EMPLOYEE, DATE, WorkType.ONSHORE,
                new BigDecimal("8.00"), true, null);

        assertThat(out).extracting(DayQuantity::getCategoryCode)
                .doesNotContain("OFFSHORE_HOLIDAY_HOURS", "QUAYSIDE_HOLIDAY_HOURS");
    }

    // ---- night derivation ----

    @Test
    void nightPortionComesFromTheScheduledShift() {
        scheduleOf(LocalTime.of(22, 0), LocalTime.of(6, 0));   // fully inside 22:00–06:00

        List<DayQuantity> out = deriver.derive(EMPLOYEE, DATE, WorkType.OFFSHORE,
                new BigDecimal("12.00"), false, null);

        assertThat(out).anySatisfy(q -> {
            assertThat(q.getCategoryCode()).isEqualTo("OFFSHORE_NIGHT_HOURS");
            assertThat(q.getQuantity()).isEqualByComparingTo("8.00");
            assertThat(q.getDerivedFrom()).isEqualTo("SHIFT_SCHEDULE");
        });
    }

    @Test
    void nightHoursNeverExceedTheHoursActuallyWorked() {
        scheduleOf(LocalTime.of(22, 0), LocalTime.of(6, 0));   // 8 night hours available

        List<DayQuantity> out = deriver.derive(EMPLOYEE, DATE, WorkType.OFFSHORE,
                new BigDecimal("4.00"), false, null);          // but only 4 worked

        assertThat(out).filteredOn(q -> "OFFSHORE_NIGHT_HOURS".equals(q.getCategoryCode()))
                .singleElement()
                .satisfies(q -> assertThat(q.getQuantity()).isEqualByComparingTo("4.00"));
    }

    @Test
    void aDayShiftProducesNoNightHours() {
        scheduleOf(LocalTime.of(9, 0), LocalTime.of(18, 0));

        List<DayQuantity> out = deriver.derive(EMPLOYEE, DATE, WorkType.OFFSHORE,
                new BigDecimal("8.00"), false, null);

        assertThat(out).extracting(DayQuantity::getCategoryCode)
                .doesNotContain("OFFSHORE_NIGHT_HOURS");
    }

    @Test
    void noScheduleMeansNoGuessedNightHours() {
        noSchedule();   // rota worker the system has no pattern for

        List<DayQuantity> out = deriver.derive(EMPLOYEE, DATE, WorkType.OFFSHORE,
                new BigDecimal("12.00"), false, null);

        assertThat(out).extracting(DayQuantity::getCategoryCode)
                .doesNotContain("OFFSHORE_NIGHT_HOURS");
    }

    // ---- the overlap maths, incl. the midnight wrap ----

    @Test
    void overlapHandlesShiftAndWindowCrossingMidnight() {
        // 18:00–06:00 shift against a 22:00–06:00 night window = 8 hours.
        assertThat(DayQuantityDeriver.overlapMinutes(
                LocalTime.of(18, 0), LocalTime.of(6, 0),
                LocalTime.of(22, 0), LocalTime.of(6, 0))).isEqualTo(480);

        // 09:00–18:00 against the same window = nothing.
        assertThat(DayQuantityDeriver.overlapMinutes(
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                LocalTime.of(22, 0), LocalTime.of(6, 0))).isZero();

        // 04:00–12:00 catches the tail of the night window = 2 hours.
        assertThat(DayQuantityDeriver.overlapMinutes(
                LocalTime.of(4, 0), LocalTime.of(12, 0),
                LocalTime.of(22, 0), LocalTime.of(6, 0))).isEqualTo(120);
    }

    // ---- leave ----

    @Test
    void nothingIsDerivedForAnEmptyDay() {
        assertThat(deriver.derive(EMPLOYEE, DATE, WorkType.OFFSHORE, BigDecimal.ZERO, true, null))
                .isEmpty();
        assertThat(deriver.derive(EMPLOYEE, DATE, null, new BigDecimal("8.00"), true, null))
                .isEmpty();
    }
}
