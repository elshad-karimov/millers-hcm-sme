package az.millers.hcm.presence.domain;

/**
 * M125 — Resolved availability state for a single employee at a single
 * instant. Priority order (highest wins): {@link #ON_LEAVE} →
 * {@link #ON_TRIP} → {@link #IN_OFFICE} → {@link #OFFLINE} →
 * {@link #NOT_SCHEDULED} → {@link #UNKNOWN}. The priority is encoded
 * in {@link az.millers.hcm.presence.service.PresenceResolver}.
 */
public enum PresenceState {
    /** Approved leave request covers today. Not available. */
    ON_LEAVE,
    /** Approved business trip covers today. Not at the office. */
    ON_TRIP,
    /** Last attendance event today was IN with no later OUT. */
    IN_OFFICE,
    /** Worked today (has an exit_time) or hasn't checked in yet. */
    OFFLINE,
    /** Schedule says today is not a working day. */
    NOT_SCHEDULED,
    /** No data sources resolved — usually new hires or terminated employees. */
    UNKNOWN
}
