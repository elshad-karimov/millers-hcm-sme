package az.millers.hcm.staffing.domain;

/** Status of a {@link PositionProfileGrant} (M250 / Phase F.2). */
public enum GrantStatus {
    /** Auto-created on hire; HR has not yet actioned. */
    PENDING,
    /** Operator confirmed the underlying grant was performed. */
    ACTIVE,
    /** Occupancy ended; the grant was pulled. */
    REVOKED,
    /** Auto-grant attempt failed (Phase F.3). */
    FAILED;

    public boolean isTerminal() { return this == REVOKED || this == FAILED; }
}
