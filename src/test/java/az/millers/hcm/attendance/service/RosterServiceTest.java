package az.millers.hcm.attendance.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;

/**
 * Pins the date-range validation rule (M110). The grid endpoint accepts
 * (from, to) as query params; a swapped pair or an unbounded range would
 * either crash the SQL or pull megabytes back to the client.
 */
class RosterServiceTest {

    @Test
    void validateRangeRejectsNullFrom() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> RosterService.validateRange(null, LocalDate.of(2026, 1, 31)));
    }

    @Test
    void validateRangeRejectsNullTo() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> RosterService.validateRange(LocalDate.of(2026, 1, 1), null));
    }

    @Test
    void validateRangeRejectsInvertedRange() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> RosterService.validateRange(
                        LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)))
                .withMessageContaining("'to' must be on or after 'from'");
    }

    @Test
    void validateRangeAcceptsSameDay() {
        LocalDate d = LocalDate.of(2026, 6, 1);
        assertThatNoException().isThrownBy(() -> RosterService.validateRange(d, d));
    }

    @Test
    void validateRangeAcceptsMaxWindow() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        assertThatNoException().isThrownBy(() -> RosterService.validateRange(
                from, from.plusDays(RosterService.MAX_RANGE_DAYS)));
    }

    @Test
    void validateRangeRejectsOversizedWindow() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> RosterService.validateRange(
                        from, from.plusDays(RosterService.MAX_RANGE_DAYS + 1)))
                .withMessageContaining("too wide");
    }
}
