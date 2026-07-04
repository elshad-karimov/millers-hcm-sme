package az.millers.hcm.compbenefits.domain;

/** Lifecycle of a {@link CompCycle} (M118). */
public enum CompCycleStatus {
    /** Editable; not yet visible to managers. */
    DRAFT,
    /** Live — managers can submit proposals, HR can approve. */
    OPEN,
    /** Past closes_on or admin-closed early. Read-only. */
    CLOSED,
    /** Pulled back without ever going live. */
    CANCELLED
}
