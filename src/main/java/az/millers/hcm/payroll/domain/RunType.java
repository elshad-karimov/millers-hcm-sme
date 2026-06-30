package az.millers.hcm.payroll.domain;

public enum RunType {
    /** Standard monthly payroll run for all employees. */
    REGULAR,

    /** Off-cycle run for a specific subset of employees (e.g., bonuses, corrections). */
    OFF_CYCLE,

    /** Final settlement for terminated employees. */
    FINAL_SETTLEMENT
}
