package az.millers.hcm.lifecycle.domain;

/**
 * Final decision recorded on a completed {@link ProbationReview}
 * (M73 / P2-01).
 *
 * <p>PASSED → confirm employment (employee status transitions out of
 * ON_PROBATION). FAILED → typically routes to TerminationService with
 * reasonCode = PROBATION_FAIL. EXTENDED → push the contract's
 * probation_end_date forward.
 */
public enum ProbationOutcome {
    PASSED,
    FAILED,
    EXTENDED
}
