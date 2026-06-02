package az.millers.hcm.lifecycle.domain;

/**
 * Probation review milestone kind (M73 / P2-01).
 *
 * <p>MID_POINT happens halfway through the probation period — a chance to
 * flag concerns early. FINAL happens shortly before {@code probation_end_date}
 * and produces the confirmation decision.
 */
public enum ProbationReviewType {
    MID_POINT,
    FINAL
}
