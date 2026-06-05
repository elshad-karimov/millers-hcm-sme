package az.millers.hcm.preboarding.domain;

/**
 * M122 — lifecycle of a pre-boarding invite.
 *
 * <pre>
 *   DRAFT ─sent→ SENT ─opens→ OPENED ─submits→ SUBMITTED ─HR confirms→ COMPLETED
 *                  │             │                │
 *                  └─revoke──────┴─revoke─────────┴─revoke→ REVOKED
 *                                                          ↑
 *                                                  expires→ EXPIRED
 * </pre>
 */
public enum PreboardingStatus {
    DRAFT,
    SENT,
    OPENED,
    SUBMITTED,
    COMPLETED,
    EXPIRED,
    REVOKED;

    public boolean isTerminal() {
        return this == COMPLETED || this == EXPIRED || this == REVOKED;
    }

    /** True iff the candidate is allowed to GET /info or POST /submit. */
    public boolean isCandidateAccessible() {
        return this == SENT || this == OPENED || this == SUBMITTED;
    }
}
