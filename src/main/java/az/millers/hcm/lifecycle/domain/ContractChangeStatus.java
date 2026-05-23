package az.millers.hcm.lifecycle.domain;

/** Contract change lifecycle (PRD 8.12.2). */
public enum ContractChangeStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    APPLIED
}
