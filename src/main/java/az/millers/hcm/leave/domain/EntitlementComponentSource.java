package az.millers.hcm.leave.domain;

/**
 * M151 — who owns an entitlement component row, which decides whether a
 * recalculation may overwrite it.
 */
public enum EntitlementComponentSource {

    /**
     * Computed by a resolver from employee/position/dependent data. Rewritten
     * on every recalculation, so editing one by hand is pointless — change the
     * driver instead.
     */
    DERIVED,

    /**
     * Entered by HR. Never touched by recalculation. This is what carries
     * components with no derivable driver (blood donation) and one-off grants
     * that must survive a recompute.
     */
    MANUAL
}
