package az.millers.hcm.lifecycle.domain;

/** Termination request lifecycle (PRD 8.11.2). */
public enum TerminationStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    PROCESSED
}
