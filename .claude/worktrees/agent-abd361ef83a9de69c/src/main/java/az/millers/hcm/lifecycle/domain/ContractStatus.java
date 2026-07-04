package az.millers.hcm.lifecycle.domain;

/**
 * Lifecycle states for an {@link EmploymentContract} (M64 / P1-03).
 *
 * <p>Transitions:
 * <pre>
 *   DRAFT      → ACTIVE (signed by both parties)
 *   DRAFT      → TERMINATED (cancelled before signing — rare)
 *   ACTIVE     → EXPIRED (end_date passed, no renewal)
 *   ACTIVE     → RENEWED (replaced by a new ACTIVE contract for the same
 *                          employee, atomically)
 *   ACTIVE     → TERMINATED (early termination, e.g. via TerminationService)
 * </pre>
 */
public enum ContractStatus {
    DRAFT,
    ACTIVE,
    EXPIRED,
    RENEWED,
    TERMINATED
}
