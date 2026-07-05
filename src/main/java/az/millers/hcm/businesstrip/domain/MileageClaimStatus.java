package az.millers.hcm.businesstrip.domain;

/** Mileage claim lifecycle (M453). */
public enum MileageClaimStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    PAID
}
