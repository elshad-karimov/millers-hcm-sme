package az.millers.hcm.recruitment.domain;

/** M296 — how a recruitment agency's placement fee is computed. */
public enum AgencyFeeType {
    /** A percentage of the hire's annualised salary. */
    PERCENT_SALARY,
    /** A flat fixed amount. */
    FIXED
}
