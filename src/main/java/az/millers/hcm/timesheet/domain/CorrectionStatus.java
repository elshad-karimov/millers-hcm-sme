package az.millers.hcm.timesheet.domain;

/** Lifecycle of a request to change an already-approved or locked day. */
public enum CorrectionStatus {
    PENDING,
    APPROVED,
    REJECTED
}
