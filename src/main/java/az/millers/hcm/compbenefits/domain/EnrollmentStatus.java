package az.millers.hcm.compbenefits.domain;

/** Lifecycle of a {@link BenefitEnrollment} (M108). */
public enum EnrollmentStatus {
    /** Active — employee is covered, contribution flows to payroll. */
    ENROLLED,
    /** Plan was offered but the employee declined coverage. */
    WAIVED,
    /** Was enrolled, now ended (resignation, transfer, etc.). */
    TERMINATED
}
