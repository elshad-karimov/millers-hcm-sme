package az.millers.hcm.performance.domain;

/** Goal lifecycle (PRD 8.13.3). */
public enum GoalStatus {
    DRAFT,
    ACTIVE,
    ON_TRACK,
    AT_RISK,
    BLOCKED,
    ACHIEVED,
    MISSED,
    CANCELLED
}
