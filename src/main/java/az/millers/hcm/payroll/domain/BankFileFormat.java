package az.millers.hcm.payroll.domain;

/**
 * Output format for the bank file generated when a {@link PayrollGroup}'s
 * payroll run is marked paid (M75 / P2-19).
 */
public enum BankFileFormat {
    CSV, SWIFT_MT103, SEPA, CUSTOM
}
