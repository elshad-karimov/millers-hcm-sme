package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.AssetEventType;
import az.millers.hcm.corehr.domain.AssetStatus;

/**
 * M124 — pins the asset lifecycle transition table that the close/
 * reissue write paths rely on. Every legal edge tested explicitly so
 * a future enum addition can't silently widen the matrix.
 */
class AssetStateMachineTest {

    // ── canTransition: legal edges ────────────────────────────────────────

    @Test
    void assignedCanReturn() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.ASSIGNED, AssetStatus.RETURNED)).isTrue();
    }

    @Test
    void assignedCanGoLost() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.ASSIGNED, AssetStatus.LOST)).isTrue();
    }

    @Test
    void assignedCanGoDamaged() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.ASSIGNED, AssetStatus.DAMAGED)).isTrue();
    }

    @Test
    void assignedCanWriteOff() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.ASSIGNED, AssetStatus.WRITTEN_OFF)).isTrue();
    }

    @Test
    void returnedCanReassign() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.RETURNED, AssetStatus.ASSIGNED)).isTrue();
    }

    @Test
    void returnedCanWriteOff() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.RETURNED, AssetStatus.WRITTEN_OFF)).isTrue();
    }

    @Test
    void damagedCanOnlyWriteOff() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.DAMAGED, AssetStatus.WRITTEN_OFF)).isTrue();
    }

    @Test
    void lostCanOnlyWriteOff() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.LOST, AssetStatus.WRITTEN_OFF)).isTrue();
    }

    // ── canTransition: illegal edges ──────────────────────────────────────

    @Test
    void terminalRefusesEverything() {
        for (AssetStatus to : AssetStatus.values()) {
            assertThat(AssetStateMachine.canTransition(AssetStatus.WRITTEN_OFF, to))
                    .as("WRITTEN_OFF → " + to)
                    .isFalse();
        }
    }

    @Test
    void noSelfLoop() {
        for (AssetStatus s : AssetStatus.values()) {
            assertThat(AssetStateMachine.canTransition(s, s))
                    .as("self-loop " + s)
                    .isFalse();
        }
    }

    @Test
    void lostCannotReassign() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.LOST, AssetStatus.ASSIGNED)).isFalse();
    }

    @Test
    void damagedCannotReturn() {
        assertThat(AssetStateMachine.canTransition(AssetStatus.DAMAGED, AssetStatus.ASSIGNED)).isFalse();
    }

    @Test
    void returnedCannotJumpToLost() {
        // No one currently holds it — it shouldn't be markable LOST without first
        // being re-assigned. The state machine enforces this.
        assertThat(AssetStateMachine.canTransition(AssetStatus.RETURNED, AssetStatus.LOST)).isFalse();
    }

    @Test
    void nullsRejected() {
        assertThat(AssetStateMachine.canTransition(null, AssetStatus.ASSIGNED)).isFalse();
        assertThat(AssetStateMachine.canTransition(AssetStatus.ASSIGNED, null)).isFalse();
    }

    // ── requireTransition ─────────────────────────────────────────────────

    @Test
    void requireTransitionThrowsOnIllegalEdge() {
        assertThatThrownBy(() ->
                AssetStateMachine.requireTransition(AssetStatus.LOST, AssetStatus.ASSIGNED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("LOST → ASSIGNED")
                .hasMessageContaining("Allowed from LOST");
    }

    @Test
    void requireTransitionPassesOnLegalEdge() {
        AssetStateMachine.requireTransition(AssetStatus.ASSIGNED, AssetStatus.RETURNED);
    }

    // ── eventTypeFor — picks the right label for each transition ──────────

    @Test
    void eventTypeForInitialAssign() {
        assertThat(AssetStateMachine.eventTypeFor(null, AssetStatus.ASSIGNED))
                .isEqualTo(AssetEventType.ASSIGN);
    }

    @Test
    void eventTypeForReassign() {
        assertThat(AssetStateMachine.eventTypeFor(AssetStatus.RETURNED, AssetStatus.ASSIGNED))
                .isEqualTo(AssetEventType.REASSIGN);
    }

    @Test
    void eventTypeForReturn() {
        assertThat(AssetStateMachine.eventTypeFor(AssetStatus.ASSIGNED, AssetStatus.RETURNED))
                .isEqualTo(AssetEventType.RETURN);
    }

    @Test
    void eventTypeForLostDamagedWriteOff() {
        assertThat(AssetStateMachine.eventTypeFor(AssetStatus.ASSIGNED, AssetStatus.LOST))
                .isEqualTo(AssetEventType.MARK_LOST);
        assertThat(AssetStateMachine.eventTypeFor(AssetStatus.ASSIGNED, AssetStatus.DAMAGED))
                .isEqualTo(AssetEventType.MARK_DAMAGED);
        assertThat(AssetStateMachine.eventTypeFor(AssetStatus.DAMAGED, AssetStatus.WRITTEN_OFF))
                .isEqualTo(AssetEventType.WRITE_OFF);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    @Test
    void isHeldOnlyOnAssigned() {
        assertThat(AssetStateMachine.isHeld(AssetStatus.ASSIGNED)).isTrue();
        for (AssetStatus s : AssetStatus.values()) {
            if (s == AssetStatus.ASSIGNED) continue;
            assertThat(AssetStateMachine.isHeld(s)).as("isHeld(" + s + ")").isFalse();
        }
    }

    @Test
    void isTerminalOnlyOnWrittenOff() {
        assertThat(AssetStateMachine.isTerminal(AssetStatus.WRITTEN_OFF)).isTrue();
        for (AssetStatus s : AssetStatus.values()) {
            if (s == AssetStatus.WRITTEN_OFF) continue;
            assertThat(AssetStateMachine.isTerminal(s)).as("isTerminal(" + s + ")").isFalse();
        }
    }
}
