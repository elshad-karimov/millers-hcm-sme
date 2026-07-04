package az.millers.hcm.corehr.domain;

/** M261 / PRD §27 — categories of approval authority on employee_approval_limit. */
public enum ApprovalLimitType {
    /** Purchase order approval ceiling. */
    PURCHASE_ORDER,
    /** Expense report approval ceiling. */
    EXPENSE_REPORT,
    /** Contract signing authority. */
    CONTRACT,
    /** Invoice payment authority. */
    INVOICE,
    /** Travel cost authority. */
    TRAVEL,
    /** Catch-all for cases not covered by the specific types above. */
    GENERAL
}
