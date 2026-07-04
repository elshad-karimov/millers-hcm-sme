package az.millers.hcm.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import az.millers.hcm.attendance.api.dto.ShiftDtos.ShiftRequest;
import az.millers.hcm.common.BadRequestException;

/**
 * Pins the M110 shift math + validation rules. Cross-midnight detection and
 * the break-vs-span boundary check are pure-static helpers; mis-implementing
 * either would either let a night shift compute to a negative duration or
 * silently accept an impossible all-break shift.
 */
class ShiftServiceTest {

    // ── crossesMidnight() ───────────────────────────────────────────────

    @Test
    void crossesMidnightTrueForNightShift() {
        assertThat(ShiftService.crossesMidnight(LocalTime.of(22, 0), LocalTime.of(6, 0))).isTrue();
        assertThat(ShiftService.crossesMidnight(LocalTime.of(23, 30), LocalTime.of(0, 30))).isTrue();
    }

    @Test
    void crossesMidnightFalseForDayShift() {
        assertThat(ShiftService.crossesMidnight(LocalTime.of(8, 0), LocalTime.of(16, 0))).isFalse();
        assertThat(ShiftService.crossesMidnight(LocalTime.of(0, 0), LocalTime.of(8, 0))).isFalse();
    }

    @Test
    void crossesMidnightFalseForNullInputs() {
        assertThat(ShiftService.crossesMidnight(null, LocalTime.NOON)).isFalse();
        assertThat(ShiftService.crossesMidnight(LocalTime.NOON, null)).isFalse();
    }

    // ── spanMinutes() ───────────────────────────────────────────────────

    @Test
    void spanMinutesForRegularDayShift() {
        // 08:00 → 16:00 = 8 hours = 480 minutes
        assertThat(ShiftService.spanMinutes(
                LocalTime.of(8, 0), LocalTime.of(16, 0), false)).isEqualTo(480);
    }

    @Test
    void spanMinutesForNightShift() {
        // 22:00 → 06:00 = 2 hours (22→24) + 6 hours (00→06) = 480 minutes
        assertThat(ShiftService.spanMinutes(
                LocalTime.of(22, 0), LocalTime.of(6, 0), true)).isEqualTo(480);
    }

    @Test
    void spanMinutesForCrossMidnightHalfHourBoundary() {
        // 23:30 → 00:30 = 60 minutes
        assertThat(ShiftService.spanMinutes(
                LocalTime.of(23, 30), LocalTime.of(0, 30), true)).isEqualTo(60);
    }

    @Test
    void spanMinutesZeroForNullInputs() {
        assertThat(ShiftService.spanMinutes(null, LocalTime.NOON, false)).isZero();
        assertThat(ShiftService.spanMinutes(LocalTime.NOON, null, false)).isZero();
    }

    // ── validate() ──────────────────────────────────────────────────────

    @Test
    void validateAcceptsCanonicalDayShift() {
        ShiftRequest req = req(LocalTime.of(9, 0), LocalTime.of(17, 0), 60, null);
        ShiftService.validate(req);
        // (no exception)
    }

    @Test
    void validateRejectsIdenticalTimes() {
        ShiftRequest req = req(LocalTime.NOON, LocalTime.NOON, 0, null);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> ShiftService.validate(req))
                .withMessageContaining("cannot be identical");
    }

    @Test
    void validateRejectsBreakLongerThanShift() {
        // 08:00 → 09:00 = 60min span; break = 60min → invalid (break must be < span)
        ShiftRequest req = req(LocalTime.of(8, 0), LocalTime.of(9, 0), 60, null);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> ShiftService.validate(req))
                .withMessageContaining("must be less than shift span");
    }

    @Test
    void validateAcceptsBreakEqualToShiftMinusOneMinute() {
        // Boundary — break = 59 in a 60-min shift is fine; break = 60 trips.
        ShiftRequest req = req(LocalTime.of(8, 0), LocalTime.of(9, 0), 59, null);
        ShiftService.validate(req);
    }

    @Test
    void validateRejectsBadHex() {
        ShiftRequest req = req(LocalTime.of(8, 0), LocalTime.of(16, 0), 60, "blue");
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> ShiftService.validate(req))
                .withMessageContaining("#RRGGBB");
    }

    @Test
    void validateAcceptsValidHex() {
        ShiftRequest req = req(LocalTime.of(8, 0), LocalTime.of(16, 0), 60, "#1677ff");
        ShiftService.validate(req);
    }

    @Test
    void validateAcceptsNullColor() {
        ShiftRequest req = req(LocalTime.of(8, 0), LocalTime.of(16, 0), 60, null);
        ShiftService.validate(req);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static ShiftRequest req(LocalTime start, LocalTime end,
                                     Integer breakMin, String color) {
        return new ShiftRequest("CODE", "Name", null, start, end, breakMin, color, true);
    }
}
