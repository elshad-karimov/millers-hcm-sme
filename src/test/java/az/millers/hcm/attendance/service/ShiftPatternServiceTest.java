package az.millers.hcm.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.PatternDayRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.PatternRequest;
import az.millers.hcm.attendance.domain.PatternAssignment;
import az.millers.hcm.common.BadRequestException;

/**
 * Pins the M111 cycle math + pattern validation rules.
 *
 * <p>The cycle position function decides which shift an employee works on
 * any given day. Off-by-one anchor, modulo on negative numbers, or ignoring
 * the anchor would silently roster the wrong shifts for entire teams — so
 * this test casts a wide boundary net.
 */
class ShiftPatternServiceTest {

    // ─── cyclePositionFor() — the heart of M111 ─────────────────────────

    @Test
    void cycleStartsAtAnchorOnStartDate() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        // anchor 0 + 0 days = position 0
        assertThat(ShiftPatternService.cyclePositionFor(start, start, 0, 7)).isEqualTo(0);
        // anchor 3 + 0 days = position 3 (mid-cycle start)
        assertThat(ShiftPatternService.cyclePositionFor(start, start, 3, 7)).isEqualTo(3);
    }

    @Test
    void cycleAdvancesByOneEachDay() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 7; i++) {
            assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(i), 0, 7))
                    .as("day +%d", i).isEqualTo(i);
        }
    }

    @Test
    void cycleWrapsCleanly() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        // 7-day cycle: day 7 wraps back to 0, day 14 also 0, day 8 is 1.
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(7), 0, 7)).isEqualTo(0);
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(14), 0, 7)).isEqualTo(0);
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(8), 0, 7)).isEqualTo(1);
    }

    @Test
    void anchorOffsetsPositionByConstant() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        // anchor=2, day=0 → 2; day=3 → 5; day=5 → 0 (because (2+5) % 7 = 0)
        assertThat(ShiftPatternService.cyclePositionFor(start, start, 2, 7)).isEqualTo(2);
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(3), 2, 7)).isEqualTo(5);
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(5), 2, 7)).isEqualTo(0);
    }

    @Test
    void cycleOfOneAlwaysReturnsAnchorZero() {
        // cycle=1 means every day is the same shift. Position is always 0
        // (only valid anchor too).
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 30; i++) {
            assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(i), 0, 1))
                    .isZero();
        }
    }

    @Test
    void fourOnFourOffPattern() {
        // 8-day cycle, anchor=0, days 0..3 are ON, 4..7 are OFF.
        // Day 0,1,2,3 work; 4,5,6,7 rest; 8 wraps back to ON.
        LocalDate start = LocalDate.of(2026, 1, 1);
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(0), 0, 8)).isEqualTo(0);
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(3), 0, 8)).isEqualTo(3);
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(4), 0, 8)).isEqualTo(4);
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(7), 0, 8)).isEqualTo(7);
        assertThat(ShiftPatternService.cyclePositionFor(start, start.plusDays(8), 0, 8)).isEqualTo(0);
    }

    @Test
    void teamMembersStaggerViaAnchor() {
        // Same start, same cycle, different anchors — they sit at different
        // positions on the same calendar day, which is how teams stagger so
        // not everyone is on the same shift at once.
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate dec1 = LocalDate.of(2026, 1, 15); // 14 days in
        // (0 + 14) % 7 = 0
        assertThat(ShiftPatternService.cyclePositionFor(start, dec1, 0, 7)).isEqualTo(0);
        // (3 + 14) % 7 = 3
        assertThat(ShiftPatternService.cyclePositionFor(start, dec1, 3, 7)).isEqualTo(3);
        // (6 + 14) % 7 = 6
        assertThat(ShiftPatternService.cyclePositionFor(start, dec1, 6, 7)).isEqualTo(6);
    }

    @Test
    void cycleRejectsZeroOrNegativeCycleDays() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ShiftPatternService.cyclePositionFor(d, d, 0, 0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ShiftPatternService.cyclePositionFor(d, d, 0, -1));
    }

    @Test
    void cycleRejectsNegativeAnchor() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ShiftPatternService.cyclePositionFor(d, d, -1, 7));
    }

    @Test
    void cycleRejectsDateBeforeStart() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate before = LocalDate.of(2026, 5, 31);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ShiftPatternService.cyclePositionFor(start, before, 0, 7));
    }

    @Test
    void cycleRejectsNullInputs() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ShiftPatternService.cyclePositionFor(null, d, 0, 7));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ShiftPatternService.cyclePositionFor(d, null, 0, 7));
    }

    @Test
    void cycleWorksOverYearBoundary() {
        // 7-day cycle straddling 2025→2026 year-end. (0 + 365) % 7 = 1.
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 1);
        // 2025 is not a leap year → exactly 365 days from Jan 1 to Jan 1.
        assertThat(ShiftPatternService.cyclePositionFor(start, end, 0, 7)).isEqualTo(1);
    }

    // ─── validatePattern() ──────────────────────────────────────────────

    @Test
    void validatePatternAcceptsCanonicalSevenDay() {
        PatternRequest req = patternReq(7, 7);
        assertThatNoException().isThrownBy(() -> ShiftPatternService.validatePattern(req));
    }

    @Test
    void validatePatternRejectsCycleOutOfRange() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> ShiftPatternService.validatePattern(patternReq(0, 0)))
                .withMessageContaining("between 1 and 365");
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> ShiftPatternService.validatePattern(patternReq(366, 366)))
                .withMessageContaining("between 1 and 365");
    }

    @Test
    void validatePatternRejectsWrongDayCount() {
        // cycle=7 but only 5 days
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> ShiftPatternService.validatePattern(patternReq(7, 5)))
                .withMessageContaining("exactly 7 entries");
    }

    @Test
    void validatePatternRejectsDuplicateDayIndex() {
        PatternRequest req = new PatternRequest(
                "DUP", "Dup", null, 3, true,
                List.of(
                        new PatternDayRequest(0, null, null),
                        new PatternDayRequest(0, null, null),  // duplicate
                        new PatternDayRequest(2, null, null)));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> ShiftPatternService.validatePattern(req))
                .withMessageContaining("Duplicate dayIndex");
    }

    @Test
    void validatePatternRejectsOutOfRangeDayIndex() {
        PatternRequest req = new PatternRequest(
                "OOR", "OOR", null, 3, true,
                List.of(
                        new PatternDayRequest(0, null, null),
                        new PatternDayRequest(1, null, null),
                        new PatternDayRequest(3, null, null)));  // 3 not in [0..2]
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> ShiftPatternService.validatePattern(req))
                .withMessageContaining("out of range");
    }

    @Test
    void validatePatternRejectsEmptyDays() {
        PatternRequest req = new PatternRequest("E", "E", null, 1, true, List.of());
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> ShiftPatternService.validatePattern(req));
    }

    // ─── pickAssignmentFor() — handles multiple historic assignments ────

    @Test
    void pickAssignmentSelectsOpenAssignmentForDateInRange() {
        UUID empId = UUID.randomUUID();
        PatternAssignment a = assignment(empId, LocalDate.of(2026, 1, 1), null);
        PatternAssignment picked = ShiftPatternService.pickAssignmentFor(
                List.of(a), LocalDate.of(2026, 6, 15));
        assertThat(picked).isSameAs(a);
    }

    @Test
    void pickAssignmentReturnsNullBeforeStartDate() {
        UUID empId = UUID.randomUUID();
        PatternAssignment a = assignment(empId, LocalDate.of(2026, 6, 1), null);
        PatternAssignment picked = ShiftPatternService.pickAssignmentFor(
                List.of(a), LocalDate.of(2026, 5, 31));
        assertThat(picked).isNull();
    }

    @Test
    void pickAssignmentReturnsNullAfterEndDate() {
        UUID empId = UUID.randomUUID();
        PatternAssignment a = assignment(empId, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30));
        PatternAssignment picked = ShiftPatternService.pickAssignmentFor(
                List.of(a), LocalDate.of(2026, 7, 1));
        assertThat(picked).isNull();
    }

    @Test
    void pickAssignmentPicksFirstMatchingHistoricEntry() {
        // First entry is end-dated but covers the test date.
        UUID empId = UUID.randomUUID();
        PatternAssignment older = assignment(empId, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30));
        PatternAssignment newer = assignment(empId, LocalDate.of(2026, 7, 1), null);
        PatternAssignment picked = ShiftPatternService.pickAssignmentFor(
                List.of(older, newer), LocalDate.of(2026, 6, 1));
        assertThat(picked).isSameAs(older);
    }

    @Test
    void pickAssignmentInclusiveBoundaries() {
        // startDate is inclusive — picking on the exact start day must succeed.
        // endDate is inclusive too — picking on the exact end day must succeed.
        UUID empId = UUID.randomUUID();
        PatternAssignment a = assignment(empId, LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30));
        assertThat(ShiftPatternService.pickAssignmentFor(List.of(a), LocalDate.of(2026, 6, 1)))
                .isSameAs(a);
        assertThat(ShiftPatternService.pickAssignmentFor(List.of(a), LocalDate.of(2026, 6, 30)))
                .isSameAs(a);
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private static PatternRequest patternReq(int cycle, int dayCount) {
        List<PatternDayRequest> days = new java.util.ArrayList<>(dayCount);
        for (int i = 0; i < dayCount; i++) {
            days.add(new PatternDayRequest(i, null, null));
        }
        return new PatternRequest("CODE", "Name", null, cycle, true, days);
    }

    private static PatternAssignment assignment(UUID empId, LocalDate start, LocalDate end) {
        PatternAssignment a = new PatternAssignment();
        a.setEmployeeId(empId);
        a.setPatternId(UUID.randomUUID());
        a.setStartDate(start);
        a.setEndDate(end);
        return a;
    }
}
