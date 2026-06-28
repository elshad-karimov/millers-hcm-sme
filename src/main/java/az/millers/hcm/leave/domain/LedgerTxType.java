package az.millers.hcm.leave.domain;

public enum LedgerTxType {
    OPENING,
    ACCRUAL,
    LEAVE_TAKEN,
    LEAVE_CANCELLED,
    ADJUSTMENT,
    CARRY_FORWARD,
    EXPIRY,
    ENCASHMENT,
    PAYROLL_CORRECTION,
    RESERVATION,
    RESERVATION_RELEASE
}
