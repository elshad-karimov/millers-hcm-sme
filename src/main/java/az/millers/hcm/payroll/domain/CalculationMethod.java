package az.millers.hcm.payroll.domain;

/**
 * M349 — How a component's amount is calculated for a given employee.
 */
public enum CalculationMethod {
    /** Uses the defaultAmount from the component (fixed). */
    FIXED_AMOUNT,

    /** Calculates as baseSalary × percentage. */
    PERCENTAGE_OF_BASE,

    /** Same as FIXED_AMOUNT — flat rate. */
    FLAT_RATE
}
