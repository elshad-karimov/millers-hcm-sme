package az.millers.hcm.organization.domain;

/**
 * Lifecycle of a structure version (PRD 8.2.2).
 */
public enum VersionStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    ACTIVE,
    REJECTED,
    ARCHIVED
}
