package az.millers.hcm.leave.domain;

/**
 * M151 — the parts an annual leave entitlement is built from.
 *
 * <p>Azerbaijani annual leave is a statutory sum, not a single number, and an
 * inspection asks for the breakdown rather than the total. Each constant is
 * one addend; {@code LeaveBalance.entitlementDays} is their sum.
 */
public enum EntitlementComponentCode {

    /** Art. 114 — the base entitlement, derived from position classification. */
    BASE(true),

    /**
     * Art. 116.1 — uplift for length of professional experience. Bracketed on
     * total professional experience, not company tenure (established from the
     * customer's register: tenure reproduced 19 of 136 rows, experience 126 of
     * 131).
     */
    SENIORITY(true),

    /** Art. 115.2 — uplift for harmful/hazardous working conditions, from the position. */
    HAZARDOUS(true),

    /** Art. 117 — uplift for women with children, from dependent records. */
    CHILDREN(true),

    /**
     * Additional rest days for blood donation. Event-driven with no derivable
     * driver, so these rows are always {@link EntitlementComponentSource#MANUAL}.
     *
     * <p>Recorded but <b>not</b> counted into the annual vacation entitlement —
     * see {@link #countsTowardAnnualEntitlement()}.
     */
    BLOOD_DONATION(false),

    /** Anything a tenant grants outside the statutory set. Always MANUAL. */
    OTHER(true);

    private final boolean countsTowardAnnualEntitlement;

    EntitlementComponentCode() {
        this(true);
    }

    EntitlementComponentCode(boolean countsTowardAnnualEntitlement) {
        this.countsTowardAnnualEntitlement = countsTowardAnnualEntitlement;
    }

    /**
     * Whether this component is part of the annual vacation entitlement total
     * written into {@code LeaveBalance.entitlementDays}.
     *
     * <p>All of them are except blood donation. That is not a guess: in the
     * customer's register, every row whose stated total disagreed with the sum
     * of its own parts disagreed by exactly the blood-donation figure — 10 rows
     * out of 10, no exceptions. Their practice treats those as separate rest
     * days earned per donation, which is also how the donation statute reads,
     * rather than as extra vacation.
     *
     * <p>They are still stored and shown, so the record of what was granted
     * survives. If those days need to be bookable, they belong as their own
     * leave type rather than inside the annual balance.
     */
    public boolean countsTowardAnnualEntitlement() {
        return countsTowardAnnualEntitlement;
    }
}
