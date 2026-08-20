package az.millers.hcm.timesheet.domain;

/**
 * Who or what put a day's numbers there.
 *
 * <p>Matters for review: a manager approving a month needs to tell an employee's
 * own declaration apart from a value the system derived, because only the former
 * is a claim that needs corroborating against attendance.
 */
public enum EntrySource {

    /** The employee typed it. */
    EMPLOYEE,
    /** Generated from attendance clock data (the pre-existing behaviour). */
    ATTENDANCE,
    /** Filled from an approved leave or sick-leave request — read-only. */
    LEAVE,
    /** Filled from the public-holiday calendar. */
    HOLIDAY,
    /** Entered by HR on the employee's behalf; always audited. */
    HR;

    public static EntrySource parse(String raw) {
        if (raw == null || raw.isBlank()) return ATTENDANCE;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ATTENDANCE;
        }
    }

    /** Sources the employee may not overwrite by hand. */
    public boolean isReadOnlyForEmployee() {
        return this == LEAVE;
    }
}
