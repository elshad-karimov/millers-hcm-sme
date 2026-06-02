package az.millers.hcm.lifecycle.domain;

/**
 * Lifecycle of a {@link ProbationReview} row (M73 / P2-01).
 *
 * <pre>
 *   SCHEDULED → COMPLETED  (with PASSED / FAILED / EXTENDED outcome)
 *   SCHEDULED → CANCELLED  (probation cut short — termination, contract renewal)
 * </pre>
 *
 * <p>The DB CHECK in V59 enforces that COMPLETED rows have both an outcome
 * and a completed_date set.
 */
public enum ProbationReviewStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED
}
