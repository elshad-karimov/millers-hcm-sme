package az.millers.hcm.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.attendance.api.dto.VarianceDtos.EmployeeRoll;
import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.SummaryStatus;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;

/**
 * Pins the M113 variance roll-up math + window guard.
 */
class RosterVarianceServiceTest {

    // ── validateWindow() ────────────────────────────────────────────────

    @Test
    void rejectsNullEndpoints() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> RosterVarianceService.validateWindow(null, LocalDate.now()));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> RosterVarianceService.validateWindow(LocalDate.now(), null));
    }

    @Test
    void rejectsInvertedRange() {
        LocalDate a = LocalDate.of(2026, 6, 1);
        LocalDate b = LocalDate.of(2026, 5, 1);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> RosterVarianceService.validateWindow(a, b))
                .withMessageContaining("'to' must be on or after 'from'");
    }

    @Test
    void acceptsSameDay() {
        LocalDate d = LocalDate.of(2026, 6, 1);
        assertThatNoException().isThrownBy(() -> RosterVarianceService.validateWindow(d, d));
    }

    @Test
    void acceptsMaxWindow() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThatNoException().isThrownBy(() -> RosterVarianceService.validateWindow(
                d, d.plusDays(RosterVarianceService.MAX_WINDOW_DAYS)));
    }

    @Test
    void rejectsOversizedWindow() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> RosterVarianceService.validateWindow(
                        d, d.plusDays(RosterVarianceService.MAX_WINDOW_DAYS + 1)))
                .withMessageContaining("too wide");
    }

    // ── rollFor() — per-employee aggregation ────────────────────────────

    @Test
    void rollSumsMinutesAcrossRows() {
        UUID empId = UUID.randomUUID();
        Employee emp = employee("Ada", "Lovelace", "Engineering");
        List<DailySummary> rows = List.of(
                rosterRow(empId, SummaryStatus.PRESENT, 0, 0, 0),    // ON_TIME
                rosterRow(empId, SummaryStatus.PRESENT, 12, 0, 0),   // LATE
                rosterRow(empId, SummaryStatus.PRESENT, 8, 0, 0),    // LATE
                rosterRow(empId, SummaryStatus.ABSENT, 0, 0, 0),     // NO_SHOW
                rosterRow(empId, SummaryStatus.PRESENT, 0, 30, 0));  // EARLY_LEAVE
        List<VarianceCategory> cats = rows.stream().map(VarianceCategory::of).toList();
        EmployeeRoll roll = RosterVarianceService.rollFor(empId, emp, rows, cats);

        assertThat(roll.rosteredDays()).isEqualTo(5);
        assertThat(roll.onTime()).isEqualTo(1);
        assertThat(roll.late()).isEqualTo(2);
        assertThat(roll.earlyLeave()).isEqualTo(1);
        assertThat(roll.noShow()).isEqualTo(1);
        assertThat(roll.unplannedOt()).isZero();
        assertThat(roll.totalLateMinutes()).isEqualTo(20);     // 12 + 8
        assertThat(roll.totalEarlyMinutes()).isEqualTo(30);
        assertThat(roll.totalOvertimeMinutes()).isZero();
        assertThat(roll.variantDays()).isEqualTo(4);            // 2 + 1 + 0 + 1
        assertThat(roll.employeeName()).isEqualTo("Ada Lovelace");
        assertThat(roll.orgUnitLabel()).isEqualTo("Engineering");
    }

    @Test
    void rollWithUnknownEmployeeFallsBackGracefully() {
        UUID empId = UUID.randomUUID();
        List<DailySummary> rows = List.of(
                rosterRow(empId, SummaryStatus.PRESENT, 0, 0, 60));  // UNPLANNED_OT
        List<VarianceCategory> cats = rows.stream().map(VarianceCategory::of).toList();
        EmployeeRoll roll = RosterVarianceService.rollFor(empId, null, rows, cats);

        assertThat(roll.employeeName()).isNull();
        assertThat(roll.orgUnitLabel()).isNull();
        assertThat(roll.unplannedOt()).isEqualTo(1);
        assertThat(roll.totalOvertimeMinutes()).isEqualTo(60);
    }

    @Test
    void rollPerfectAttendanceShowsZeroVariance() {
        UUID empId = UUID.randomUUID();
        Employee emp = employee("Grace", "Hopper", "Engineering");
        List<DailySummary> rows = List.of(
                rosterRow(empId, SummaryStatus.PRESENT, 0, 0, 0),
                rosterRow(empId, SummaryStatus.PRESENT, 0, 0, 0),
                rosterRow(empId, SummaryStatus.PRESENT, 0, 0, 0));
        List<VarianceCategory> cats = rows.stream().map(VarianceCategory::of).toList();
        EmployeeRoll roll = RosterVarianceService.rollFor(empId, emp, rows, cats);

        assertThat(roll.rosteredDays()).isEqualTo(3);
        assertThat(roll.onTime()).isEqualTo(3);
        assertThat(roll.variantDays()).isZero();
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static DailySummary rosterRow(UUID empId, SummaryStatus status,
                                            int lateMin, int earlyMin, int otMin) {
        DailySummary s = new DailySummary();
        s.setId(UUID.randomUUID());
        s.setEmployeeId(empId);
        s.setWorkDate(LocalDate.of(2026, 6, 1));
        s.setSource("ROSTER");
        s.setStatus(status);
        s.setLateMinutes(lateMin);
        s.setEarlyMinutes(earlyMin);
        s.setOvertimeMinutes(otMin);
        return s;
    }

    private static Employee employee(String first, String last, String dept) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setFirstName(first);
        e.setLastName(last);
        e.setDepartmentName(dept);
        return e;
    }
}
