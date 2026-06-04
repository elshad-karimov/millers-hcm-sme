package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the M102 comp-ratio / salary-planning analytics surface.
 *
 * <p>Comp ratio = actual base salary / grade midpoint × 100.
 * A ratio below 80 is typically a retention/flight-risk signal;
 * above 120 may indicate grade creep or equity issues.
 */
public final class CompRatioDtos {
    private CompRatioDtos() {}

    /** Risk classification derived from comp ratio. */
    public enum CompRiskLevel {
        /** ratio &lt; 80 — below range, flight risk */
        BELOW_RANGE,
        /** 80 &le; ratio &lt; 90 — low in range */
        LOW_IN_RANGE,
        /** 90 &le; ratio &le; 110 — mid-point (healthy) */
        AT_MIDPOINT,
        /** 110 &lt; ratio &le; 120 — high in range */
        HIGH_IN_RANGE,
        /** ratio &gt; 120 — above range, equity concern */
        ABOVE_RANGE,
        /** Grade has no min/max salary configured */
        NO_BAND
    }

    /** One employee's comp-ratio row. */
    public record EmployeeCompRatioRow(
            UUID employeeId,
            String employeeNo,
            String fullName,
            String department,
            String gradeCode,
            String gradeName,
            BigDecimal minSalary,
            BigDecimal midpointSalary,
            BigDecimal maxSalary,
            BigDecimal actualSalary,
            /** Actual / midpoint × 100, null when grade has no midpoint. */
            BigDecimal compRatio,
            /**  actualSalary − midpointSalary; null when no midpoint. */
            BigDecimal salaryVsMidpoint,
            CompRiskLevel riskLevel) {}

    /** Grade-level rollup — one row per grade with aggregate stats. */
    public record GradeBandRow(
            String gradeCode,
            String gradeName,
            int employeeCount,
            BigDecimal minSalary,
            BigDecimal midpointSalary,
            BigDecimal maxSalary,
            BigDecimal avgActualSalary,
            BigDecimal avgCompRatio,
            int belowRange,
            int atMidpoint,
            int aboveRange) {}

    /** Top-level comp-ratio report. */
    public record CompRatioReport(
            int totalEmployees,
            int noGradeCount,
            int noBandCount,
            BigDecimal overallAvgCompRatio,
            int flightRiskCount,
            List<EmployeeCompRatioRow> employees,
            List<GradeBandRow> gradeBands) {}
}
