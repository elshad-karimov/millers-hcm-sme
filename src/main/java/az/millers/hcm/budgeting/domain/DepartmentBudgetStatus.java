package az.millers.hcm.budgeting.domain;

public enum DepartmentBudgetStatus {
    DRAFT,      // Being edited
    SUBMITTED,  // Awaiting approval
    APPROVED,   // Approved and active
    REJECTED    // Rejected, sent back to draft
}
