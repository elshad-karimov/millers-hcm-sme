package az.millers.hcm.recruitment.domain;

/**
 * Offer lifecycle (M276 expanded — Recruitment PRD §29-§30).
 *
 * <pre>
 *   DRAFT ──submit──► PENDING_APPROVAL ──approve──► APPROVED ──send──► SENT
 *     ▲                     │                          │                 │
 *     └─────reject/return───┘                     RESCINDED     ACCEPTED / REJECTED / EXPIRED
 * </pre>
 *
 * <p>PRD §70: "Offer cannot be sent before approval" — enforced in
 * {@code OfferService.validateTransition}.
 */
public enum OfferStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    SENT,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    RESCINDED
}
