package az.millers.hcm.timesheet.domain;

/**
 * Where one day stands in the approval of its month.
 *
 * <p>Exists so a manager can return two bad days without re-opening the
 * twenty-nine good ones. The month cannot reach APPROVED while any day is
 * still {@link #RETURNED}.
 */
public enum DayApprovalState {

    /** Submitted, not yet judged. */
    PENDING,
    /** The approver accepted this day. */
    APPROVED,
    /** Sent back with a reason; only these days re-open for the employee. */
    RETURNED;

    public static DayApprovalState parse(String raw) {
        if (raw == null || raw.isBlank()) return PENDING;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
