package az.millers.hcm.budgeting.domain;

public enum BudgetCycleStatus {
    DRAFT,      // Being created
    OPEN,       // Accepting department budget submissions
    LOCKED,     // No new submissions, budgets still mutable
    CLOSED      // Immutable, archived
}
