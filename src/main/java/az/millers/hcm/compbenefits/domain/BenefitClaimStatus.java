package az.millers.hcm.compbenefits.domain;

/** HCM_11 M381 — benefit reimbursement claim lifecycle. */
public enum BenefitClaimStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    PAID,
    CANCELLED
}
