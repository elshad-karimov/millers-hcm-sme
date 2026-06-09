package az.millers.hcm.staffing.domain;

/**
 * Lifecycle status of the position record itself (PRD §10 — separate
 * from {@link VacancyState}, which captures occupancy, and from any
 * future funding/approval axes).
 *
 * <p>Allowed transitions (M243):
 * <pre>
 *   DRAFT ──submit──▶ PENDING_APPROVAL ──approve──▶ APPROVED ──activate──▶ ACTIVE
 *     ▲                       │                                                │
 *     └────reject─────────────┘                                                │
 *                                                                              │
 *   ACTIVE ──freeze──▶ FROZEN ──unfreeze──▶ ACTIVE                              │
 *   ACTIVE ──markUnderReview──▶ UNDER_REVIEW ──finishReview──▶ ACTIVE           │
 *                                                                              │
 *   ACTIVE | FROZEN | UNDER_REVIEW ──close──▶ CLOSED ──archive──▶ ARCHIVED ◀───┘
 * </pre>
 *
 * <p>Pre-M243 rows are either {@code ACTIVE} or {@code CLOSED}, which both
 * remain valid — the migration is purely additive.
 */
public enum PositionStatus {
    /** Created, not yet submitted for approval. Editable; no recruitment. */
    DRAFT,
    /** Submitted, waiting for HR / Finance / executive approval. */
    PENDING_APPROVAL,
    /** Approved, but not yet flipped on. Useful when funding lags. */
    APPROVED,
    /** Live: recruitment allowed, employees can be assigned. */
    ACTIVE,
    /** Active but temporarily frozen — no recruitment, no new fills. */
    FROZEN,
    /** Under restructuring / re-grading; treated like FROZEN for filling. */
    UNDER_REVIEW,
    /** Soft-deleted. Kept for history, payroll costing, audits. */
    CLOSED,
    /** Long-term cold storage — hidden from default lists. */
    ARCHIVED;

    /** True if a new employee may be assigned to this position. */
    public boolean isFillable() {
        return this == ACTIVE;
    }

    /** True if the row is effectively retired. */
    public boolean isTerminal() {
        return this == CLOSED || this == ARCHIVED;
    }
}
