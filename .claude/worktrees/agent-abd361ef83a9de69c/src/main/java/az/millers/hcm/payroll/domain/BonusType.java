package az.millers.hcm.payroll.domain;

/** Configurable bonus types (PRD 8.9.5). */
public enum BonusType {
    FIXED,
    PERCENTAGE,
    PERFORMANCE,
    ONE_TIME,
    KPI,
    DEPARTMENT,
    MANUAL,
    IMPORTED
}
