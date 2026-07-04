package az.millers.hcm.payroll.domain;

/**
 * Output format for the bank file generated when a payroll run is marked paid
 * (M75 / P2-19 / M163).
 *
 * <p>The four Azerbaijani bank templates (ABB, KAPITAL, PASHA, RESPUBLIKA) use
 * the column layout required by each bank's corporate salary-disbursement API /
 * upload portal. CSV is the generic baseline used when no specific bank is known.
 */
public enum BankFileFormat {
    /** Generic baseline CSV — safe fallback. */
    CSV,
    /** ABB Bank Azerbaijan salary-disbursement CSV. */
    ABB,
    /** Kapital Bank Azerbaijan salary CSV (semicolon-delimited). */
    KAPITAL,
    /** PASHA Bank Azerbaijan salary-disbursement CSV. */
    PASHA,
    /** Bank Respublika Azerbaijan salary-disbursement CSV (tab-delimited). */
    RESPUBLIKA,
    SWIFT_MT103,
    SEPA,
    CUSTOM
}
