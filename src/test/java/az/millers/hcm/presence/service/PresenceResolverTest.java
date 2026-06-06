package az.millers.hcm.presence.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import az.millers.hcm.presence.domain.PresenceState;
import az.millers.hcm.presence.service.PresenceResolver.EventSnapshot;
import az.millers.hcm.presence.service.PresenceResolver.Outcome;
import az.millers.hcm.presence.service.PresenceResolver.Signals;

/**
 * M125 — pins the resolver priority ladder. This is the only place the
 * production code chooses between conflicting signals (e.g. someone
 * who badged in this morning but also has an approved sick-leave for
 * today — leave wins, not the attendance event).
 */
class PresenceResolverTest {

    private static final OffsetDateTime T = OffsetDateTime.parse("2026-06-05T09:00:00Z");

    private static Signals s(EventSnapshot last, boolean leave, boolean trip, boolean working) {
        return new Signals(last, leave, trip, working);
    }

    // ── priority ladder ──────────────────────────────────────────────────

    @Test
    void leaveTrumpsTrip() {
        Outcome o = PresenceResolver.resolve(s(null, true, true, true));
        assertThat(o.state()).isEqualTo(PresenceState.ON_LEAVE);
    }

    @Test
    void leaveTrumpsInOffice() {
        Outcome o = PresenceResolver.resolve(s(
                new EventSnapshot("IN", T), true, false, true));
        assertThat(o.state()).isEqualTo(PresenceState.ON_LEAVE);
    }

    @Test
    void tripTrumpsInOffice() {
        Outcome o = PresenceResolver.resolve(s(
                new EventSnapshot("IN", T), false, true, true));
        assertThat(o.state()).isEqualTo(PresenceState.ON_TRIP);
    }

    @Test
    void inOfficeFromIn() {
        Outcome o = PresenceResolver.resolve(s(
                new EventSnapshot("IN", T), false, false, true));
        assertThat(o.state()).isEqualTo(PresenceState.IN_OFFICE);
        assertThat(o.since()).isEqualTo(T);
    }

    @Test
    void offlineFromOut() {
        Outcome o = PresenceResolver.resolve(s(
                new EventSnapshot("OUT", T), false, false, true));
        assertThat(o.state()).isEqualTo(PresenceState.OFFLINE);
        assertThat(o.since()).isEqualTo(T);
    }

    @Test
    void caseInsensitiveEventType() {
        Outcome in = PresenceResolver.resolve(s(
                new EventSnapshot("in", T), false, false, true));
        assertThat(in.state()).isEqualTo(PresenceState.IN_OFFICE);
        Outcome out = PresenceResolver.resolve(s(
                new EventSnapshot("Out", T), false, false, true));
        assertThat(out.state()).isEqualTo(PresenceState.OFFLINE);
    }

    @Test
    void notScheduledOnNonWorkingDay() {
        Outcome o = PresenceResolver.resolve(s(null, false, false, false));
        assertThat(o.state()).isEqualTo(PresenceState.NOT_SCHEDULED);
    }

    @Test
    void offlineFallbackOnWorkingDayWithNoEvent() {
        Outcome o = PresenceResolver.resolve(s(null, false, false, true));
        assertThat(o.state()).isEqualTo(PresenceState.OFFLINE);
        assertThat(o.since()).isNull();
    }

    @Test
    void unknownOnNullSignals() {
        Outcome o = PresenceResolver.resolve(null);
        assertThat(o.state()).isEqualTo(PresenceState.UNKNOWN);
    }

    @Test
    void unrecognisedEventTypeFallsThroughToScheduleLogic() {
        // An event row with neither IN nor OUT (bad data) should not
        // get treated as IN_OFFICE — fall through to schedule logic.
        Outcome workingDay = PresenceResolver.resolve(s(
                new EventSnapshot("WHATEVER", T), false, false, true));
        assertThat(workingDay.state()).isEqualTo(PresenceState.OFFLINE);
        Outcome offDay = PresenceResolver.resolve(s(
                new EventSnapshot("WHATEVER", T), false, false, false));
        assertThat(offDay.state()).isEqualTo(PresenceState.NOT_SCHEDULED);
    }

    // ── counts ───────────────────────────────────────────────────────────

    @Test
    void countsAggregatesByState() {
        var counts = PresenceResolver.counts(List.of(
                new Outcome(PresenceState.IN_OFFICE, T),
                new Outcome(PresenceState.IN_OFFICE, T),
                new Outcome(PresenceState.ON_LEAVE, null),
                new Outcome(PresenceState.OFFLINE, T)));
        assertThat(counts.get(PresenceState.IN_OFFICE)).isEqualTo(2L);
        assertThat(counts.get(PresenceState.ON_LEAVE)).isEqualTo(1L);
        assertThat(counts.get(PresenceState.OFFLINE)).isEqualTo(1L);
        assertThat(counts.get(PresenceState.ON_TRIP)).isEqualTo(0L);
        assertThat(counts.get(PresenceState.NOT_SCHEDULED)).isEqualTo(0L);
        assertThat(counts.get(PresenceState.UNKNOWN)).isEqualTo(0L);
    }

    @Test
    void countsEmptyInputReturnsAllZeros() {
        var counts = PresenceResolver.counts(List.of());
        for (PresenceState s : PresenceState.values()) {
            assertThat(counts.get(s)).as(s.name()).isEqualTo(0L);
        }
    }

    @Test
    void countsNullInputReturnsAllZeros() {
        var counts = PresenceResolver.counts(null);
        for (PresenceState s : PresenceState.values()) {
            assertThat(counts.get(s)).as(s.name()).isEqualTo(0L);
        }
    }

    @Test
    void countsSkipsNullOutcomes() {
        var counts = PresenceResolver.counts(java.util.Arrays.asList(
                new Outcome(PresenceState.IN_OFFICE, T), null));
        assertThat(counts.get(PresenceState.IN_OFFICE)).isEqualTo(1L);
    }
}
