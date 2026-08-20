package az.millers.hcm.timesheet.domain;

/**
 * Where and under what regime a day was worked.
 *
 * <p>This is the dimension the old single {@code primary_code} could not carry:
 * the same employee works offshore, onshore and quayside within one month and
 * each is priced differently. The work type decides which time categories the
 * employee may enter against a day (see {@code time_category.applies_to}).
 *
 * <p>Deliberately operational, not monetary — the employee describes what
 * happened, payroll decides what it is worth.
 */
public enum WorkType {

    /** Normal work at an onshore site or office. */
    ONSHORE,
    /** Work at an offshore facility. */
    OFFSHORE,
    /** Work at the quayside. */
    QUAYSIDE,
    /** Travelling on an approved business trip. */
    BUSINESS_TRIP,
    /** Working away from a company site. */
    REMOTE,
    /** Covered by an approved leave request. */
    LEAVE,
    /** Covered by an approved sick-leave request. */
    SICK,
    /** Rest day, weekend or public holiday that was not worked. */
    NON_WORKING;

    /** Lenient parse — unknown or blank yields {@code null} rather than throwing. */
    public static WorkType parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True when the day is covered by leave rather than work. */
    public boolean isLeave() {
        return this == LEAVE || this == SICK;
    }
}
