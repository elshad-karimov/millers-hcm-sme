package az.millers.hcm.recruitment.domain;

/**
 * Requisition lifecycle (M274 expanded from the original 5 states).
 *
 * <pre>
 *   DRAFT → PENDING_APPROVAL → APPROVED → OPEN → PUBLISHED
 *                ↓                          ↕        ↕
 *             REJECTED                  PAUSED / ON_HOLD
 *
 *   OPEN/PUBLISHED → FILLED (all openings hired)
 *   any non-terminal → CLOSED / CANCELLED
 * </pre>
 *
 * <p>Pre-M275 vacancies were created directly in OPEN; the approval
 * states (DRAFT / PENDING_APPROVAL / APPROVED / REJECTED) are wired
 * into a state machine by the M275 requisition-approval workflow.
 */
public enum VacancyStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    OPEN,
    PUBLISHED,
    PAUSED,
    ON_HOLD,
    FILLED,
    CLOSED,
    CANCELLED;

    /** True when the requisition is actively accepting candidates. */
    public boolean isAccepting() {
        return this == OPEN || this == PUBLISHED;
    }

    /** True when no further transitions are meaningful. */
    public boolean isTerminal() {
        return this == FILLED || this == CLOSED || this == CANCELLED || this == REJECTED;
    }
}
