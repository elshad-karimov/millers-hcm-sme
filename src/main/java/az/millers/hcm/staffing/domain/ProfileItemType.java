package az.millers.hcm.staffing.domain;

/**
 * Type of profile item attached to a position (M248 / PRD §25, §26, §27, §28, §29, §30).
 *
 * <p>Each value names a distinct downstream concern. Phase F delivers
 * the unified definition; Phase F.2 will wire each type into its
 * native module so the auto-grant happens on occupancy.
 */
public enum ProfileItemType {
    /** Recurring monthly allowance (vehicle, phone, housing, meal, hazard…). */
    ALLOWANCE,
    /** A document the new hire must submit (driver licence, medical cert, etc.). */
    REQUIRED_DOCUMENT,
    /** A mandatory training / course / certification. */
    TRAINING,
    /** Physical equipment the company provides (laptop, uniform, POS, vehicle). */
    EQUIPMENT,
    /** ERP / HCM / POS access bundle granted on assignment. */
    ACCESS_ROLE,
    /** Free-form onboarding to-do not covered by the above. */
    CHECKLIST_ITEM,
    /** Spending / expense / leave / hiring authority limit. */
    APPROVAL_LIMIT;

    /** True if {@code value_amount} is meaningful for this type. */
    public boolean hasAmount() {
        return this == ALLOWANCE || this == APPROVAL_LIMIT;
    }
}
