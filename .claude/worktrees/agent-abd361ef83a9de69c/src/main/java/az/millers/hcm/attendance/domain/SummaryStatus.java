package az.millers.hcm.attendance.domain;

/** Outcome of the attendance engine for a single employee-day. */
public enum SummaryStatus {
    PRESENT,
    PARTIAL,
    ABSENT,
    NON_WORKING_DAY,
    NO_SCHEDULE
}
