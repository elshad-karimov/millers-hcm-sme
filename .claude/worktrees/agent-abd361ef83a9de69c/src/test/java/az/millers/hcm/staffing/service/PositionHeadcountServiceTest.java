package az.millers.hcm.staffing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pure-math pinning test for the M109 gate.
 *
 * <p>The wiring tests (gate trips on direct-hire / vacancy-creation / position
 * swap, occupancy stays in sync, reconciliation fixes drift) live in the
 * Spring integration suite because they need a real EntityManager.
 *
 * <p>This file pins the one piece of behaviour that's worth catching at the
 * unit-test layer: the seat-availability predicate. Inverting it (or applying
 * an off-by-one) would let direct hires silently exceed the approved
 * headcount, which is exactly the bug this milestone exists to prevent.
 */
class PositionHeadcountServiceTest {

    @Test
    void hasRoomWhenOccupiedBelowApproved() {
        assertThat(PositionHeadcountService.hasRoom(5, 4)).isTrue();
        assertThat(PositionHeadcountService.hasRoom(1, 0)).isTrue();
        assertThat(PositionHeadcountService.hasRoom(10, 9)).isTrue();
    }

    @Test
    void hasNoRoomWhenAtCapacity() {
        // Boundary — the bug we're guarding against. occupied == approved
        // means the seat is full and the gate must refuse new fills.
        assertThat(PositionHeadcountService.hasRoom(5, 5)).isFalse();
        assertThat(PositionHeadcountService.hasRoom(1, 1)).isFalse();
        assertThat(PositionHeadcountService.hasRoom(10, 10)).isFalse();
    }

    @Test
    void hasNoRoomWhenOverCapacity() {
        // Possible after a drift incident — the gate must still refuse new
        // fills, leaving reconciliation to bring the counter back in line.
        assertThat(PositionHeadcountService.hasRoom(5, 6)).isFalse();
        assertThat(PositionHeadcountService.hasRoom(5, 100)).isFalse();
    }

    @Test
    void hasNoRoomWhenApprovedIsZero() {
        // A position with zero approved headcount is a planning placeholder —
        // it must never accept a hire until HR opens budget for it.
        assertThat(PositionHeadcountService.hasRoom(0, 0)).isFalse();
    }
}
