package az.millers.hcm.timesheet.domain;

public enum TimesheetStatus {
    DRAFT,
    SUBMITTED,
    /**
     * V324 — the direct manager has approved; awaiting HR sign-off.
     *
     * Not editable by the employee and not recallable: a manager has already
     * put their name to these numbers.
     */
    PENDING_HR,
    /**
     * Sent back with named days to fix.
     *
     * <p>Distinct from a reject on purpose: returned means "correct these days
     * and resubmit" and preserves everything the employee entered, while a
     * reject means the submission should not proceed at all. Collapsing the two
     * — as the pre-slice-2 workflow callback did, mapping RETURNED to DRAFT —
     * threw away the manager's instruction and the employee's place.
     */
    RETURNED,
    APPROVED,
    LOCKED,
    REOPENED;

    /** States in which the employee may edit their days. */
    public boolean isEditableByEmployee() {
        return this == DRAFT || this == RETURNED || this == REOPENED;
    }
}
