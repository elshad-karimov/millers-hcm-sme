package az.millers.hcm.lifecycle.domain;

public enum AssetReturnStatus {
    PENDING_RETURN,
    RETURNED,
    RETURNED_DAMAGED,
    MISSING,
    DEDUCTION_APPROVED,
    WRITTEN_OFF,
    WAIVED
}
