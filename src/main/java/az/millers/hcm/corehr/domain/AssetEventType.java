package az.millers.hcm.corehr.domain;

/**
 * M124 — the kinds of events captured in the asset_event log.
 *
 * <pre>
 *   ASSIGN           — first handover or after a reissue cycle
 *   RETURN           — handed back, asset back IN_STORE (RETURNED status)
 *   MARK_LOST        — terminal LOST
 *   MARK_DAMAGED     — terminal DAMAGED
 *   WRITE_OFF        — terminal WRITTEN_OFF
 *   REASSIGN         — pair: a RETURN by the previous holder and an
 *                      ASSIGN to the new holder, recorded as one row
 *                      so the log shows the transfer without two
 *                      half-events.
 *   UPDATE_CONDITION — admin tweak to condition mid-tenure
 * </pre>
 */
public enum AssetEventType {
    ASSIGN,
    RETURN,
    MARK_LOST,
    MARK_DAMAGED,
    WRITE_OFF,
    REASSIGN,
    UPDATE_CONDITION
}
