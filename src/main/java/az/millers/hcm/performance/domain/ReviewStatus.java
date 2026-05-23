package az.millers.hcm.performance.domain;

/** Performance review lifecycle (PRD 8.13.4). */
public enum ReviewStatus {
    DRAFT,
    SELF_IN_PROGRESS,
    SELF_SUBMITTED,
    MANAGER_IN_PROGRESS,
    MANAGER_SUBMITTED,
    PENDING_APPROVAL,
    CALIBRATING,
    APPROVED,
    COMPLETED,
    REJECTED,
    CANCELLED
}
