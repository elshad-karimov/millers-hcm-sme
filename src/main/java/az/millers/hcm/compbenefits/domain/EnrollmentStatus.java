package az.millers.hcm.compbenefits.domain;

/**
 * Lifecycle of a {@link BenefitEnrollment} (M108 + HCM_11 M376/M377).
 *
 * <p>Direct admin enrol goes straight to {@link #ENROLLED}. The approval flow (M377)
 * walks {@link #DRAFT} → {@link #PENDING_APPROVAL} → {@link #ENROLLED} (or {@link #REJECTED}).
 */
public enum EnrollmentStatus {
    /** Created, not yet submitted for approval. */
    DRAFT,
    /** Submitted — an approval workflow is running. */
    PENDING_APPROVAL,
    /** Active — employee is covered, contribution flows to payroll. */
    ENROLLED,
    /** Plan was offered but the employee declined coverage. */
    WAIVED,
    /** Temporarily paused (e.g. unpaid leave) — coverage + deduction suspended. */
    SUSPENDED,
    /** Was enrolled, now ended (resignation, transfer, plan expiry, etc.). */
    TERMINATED,
    /** A draft / pending enrollment cancelled before it became active. */
    CANCELLED,
    /** Approval was rejected. */
    REJECTED
}
