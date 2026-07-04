package az.millers.hcm.organization.domain;

/**
 * M144 — four-state lifecycle machine for an org unit (§26).
 *
 * <pre>
 *  PLANNED ──open──► ACTIVE ──announce──► CLOSING ──close──► CLOSED
 *                      ▲                     │                  │
 *                      └──────cancel─────────┘                  │
 *                      └──────────────────reopen────────────────┘
 * </pre>
 */
public enum OrgUnitLifecycleState {

    /** Unit approved but not yet operational (e.g. new branch fitting out). */
    PLANNED,

    /** Normal operational state. */
    ACTIVE,

    /** Closure announced; employees should be reassigned before the close date. */
    CLOSING,

    /** Permanently closed. {@code org_unit.active} is set to {@code false}. */
    CLOSED
}
