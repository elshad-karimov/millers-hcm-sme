package az.millers.hcm.learning.domain;

public enum EnrolledVia {
    SELF_ENROLLED,
    ASSIGNED,
    /** Auto-enrolled by the learning-path engine when the previous step was passed. */
    PATH
}
