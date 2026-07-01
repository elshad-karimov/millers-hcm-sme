package az.millers.hcm.compensation.domain;

/**
 * M359 — How to handle compensation changes that exceed budget.
 */
public enum BudgetExceededPolicy {
    /** Log a warning but allow the change. */
    WARNING,
    /** Block the change until budget is adjusted. */
    HARD_STOP,
    /** Route to exception approval workflow. */
    EXCEPTION_APPROVAL
}
