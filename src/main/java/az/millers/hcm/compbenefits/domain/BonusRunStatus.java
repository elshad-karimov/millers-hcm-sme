package az.millers.hcm.compbenefits.domain;

public enum BonusRunStatus {
    DRAFT,
    GENERATED,
    /** Submitted for approval — no edits allowed. */
    PENDING_APPROVAL,
    /** Approved by Finance / HR Director — ready to push to payroll. */
    APPROVED,
    PUSHED,
    CANCELLED
}
