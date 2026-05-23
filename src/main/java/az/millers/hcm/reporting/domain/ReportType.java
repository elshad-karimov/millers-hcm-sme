package az.millers.hcm.reporting.domain;

/** The eight report aggregations exposed by {@code /api/reports/*}. */
public enum ReportType {
    HEADCOUNT,
    ATTRITION,
    PAYROLL,
    LEAVE,
    ATTENDANCE,
    TRAINING,
    PERFORMANCE,
    RECRUITMENT
}
