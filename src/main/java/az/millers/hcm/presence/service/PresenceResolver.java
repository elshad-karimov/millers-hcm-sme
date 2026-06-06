package az.millers.hcm.presence.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import az.millers.hcm.presence.domain.PresenceState;

/**
 * M125 — pure-static state resolution. Given pre-loaded source signals
 * for an employee (last attendance event, has-approved-leave,
 * has-approved-trip, is-working-day), pick the single state to display.
 *
 * <p>Mockito-free so the priority logic gets pinned by plain JUnit. The
 * service composes this over the full result set; this class never
 * reaches a DB.
 */
public final class PresenceResolver {

    public record Signals(
            /** Most recent attendance event today, null if none. */
            EventSnapshot lastEvent,
            boolean hasApprovedLeaveToday,
            boolean hasApprovedTripToday,
            boolean isWorkingDayToday) {}

    public record EventSnapshot(
            /** "IN" or "OUT". */
            String eventType,
            OffsetDateTime eventTime) {}

    public record Outcome(PresenceState state, OffsetDateTime since) {}

    private PresenceResolver() {}

    /**
     * Apply the priority ladder. The {@code since} timestamp is the
     * "since when" displayed on the card — last event time for IN/OFFLINE,
     * null for the others (the SPA reads today's date for the duration).
     */
    public static Outcome resolve(Signals s) {
        if (s == null) return new Outcome(PresenceState.UNKNOWN, null);
        // Highest-priority states — they trump attendance because
        // even if someone's badge pinged the office on the way home
        // yesterday, an approved leave today wins.
        if (s.hasApprovedLeaveToday()) return new Outcome(PresenceState.ON_LEAVE, null);
        if (s.hasApprovedTripToday())  return new Outcome(PresenceState.ON_TRIP,  null);

        // Attendance signal.
        if (s.lastEvent() != null) {
            String et = s.lastEvent().eventType();
            if ("IN".equalsIgnoreCase(et)) {
                return new Outcome(PresenceState.IN_OFFICE, s.lastEvent().eventTime());
            }
            if ("OUT".equalsIgnoreCase(et)) {
                return new Outcome(PresenceState.OFFLINE, s.lastEvent().eventTime());
            }
        }

        // No attendance today.
        if (!s.isWorkingDayToday()) {
            return new Outcome(PresenceState.NOT_SCHEDULED, null);
        }
        // Working day with no events → just hasn't badged in yet.
        return new Outcome(PresenceState.OFFLINE, null);
    }

    /**
     * Roll up a list of resolved outcomes into a state→count map. The
     * controller exposes this as the counts row above the grid.
     */
    public static Map<PresenceState, Long> counts(List<Outcome> outcomes) {
        Map<PresenceState, Long> out = new HashMap<>();
        for (PresenceState s : PresenceState.values()) out.put(s, 0L);
        if (outcomes != null) {
            for (Outcome o : outcomes) {
                if (o == null || o.state() == null) continue;
                out.merge(o.state(), 1L, Long::sum);
            }
        }
        return out;
    }
}
