package az.millers.hcm.timesheet.domain;

/** Whether a monthly timesheet period still accepts changes. */
public enum PeriodStatus {

    /** Employees may submit; managers may approve. */
    OPEN,
    /**
     * Closed. No submission, no approval, no edit — and the only state in which
     * payroll may consume the period.
     */
    LOCKED
}
