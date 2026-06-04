package az.millers.hcm.compbenefits.domain;

/** Lifecycle of a single {@link CompProposal} (M118). */
public enum CompProposalStatus {
    /** Manager is still editing. Not visible to HR. */
    DRAFT,
    /** Manager has submitted; awaiting HR decision. */
    SUBMITTED,
    /** HR approved — the proposal feeds {@code EmployeeCompensation} on
     *  {@code effective_on}. */
    APPROVED,
    /** HR rejected — proposal is final but creates no comp change. */
    REJECTED
}
