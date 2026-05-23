package az.millers.hcm.corehr.domain;

/**
 * Configurable employee statuses (PRD 8.1.2). Status transitions are audited.
 */
public enum EmploymentStatus {
    ACTIVE,
    ON_PROBATION,
    ON_LEAVE,
    ON_BUSINESS_TRIP,
    SUSPENDED,
    TERMINATED,
    RETIRED,
    CONTRACTOR,
    INTERN
}
