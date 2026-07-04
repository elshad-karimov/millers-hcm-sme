package az.millers.hcm.payroll.domain;

/**
 * Cadence at which a {@link PayrollGroup} runs payroll (M75 / P2-19).
 */
public enum PayCycle {
    MONTHLY, BI_WEEKLY, WEEKLY, SEMI_MONTHLY
}
