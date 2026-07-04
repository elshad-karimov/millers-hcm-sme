package az.millers.hcm.corehr.domain;

/**
 * M135 — reason a dependent's benefit eligibility ended.
 *
 * <p>Whitelist mirrored in {@code V93__dependent_eligibility_end.sql}
 * as a CHECK constraint. Free-form details still live in the
 * {@code notes} column.
 */
public enum DependentEligibilityEndReason {
    /** Child past the dependent-age cap (typically 18 or 26 depending on plan). */
    AGED_OUT,
    /** Spouse separation. */
    DIVORCE,
    /** Bereavement. */
    DEATH,
    /** Child gained own coverage by marrying. */
    MARRIED,
    /** Child got their own job + employer benefits. */
    EMPLOYED,
    /** Full-time student status ended — relevant for student-extension policies. */
    STUDENT_STATUS_LOST,
    /** Catch-all; HR adds context in {@code notes}. */
    OTHER
}
