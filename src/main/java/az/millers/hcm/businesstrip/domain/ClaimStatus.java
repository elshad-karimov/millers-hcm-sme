package az.millers.hcm.businesstrip.domain;

/** Lifecycle of an {@link ExpenseClaim} (M104). Mirrors the V71 CHECK. */
public enum ClaimStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    PAID
}
