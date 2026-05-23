package az.millers.hcm.reporting.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lightweight DTOs for the dashboard analytics endpoints (PRD Section 10.3).
 *
 * <p>All records are designed for direct JSON serialisation; amounts are
 * {@link BigDecimal} or {@code double} depending on precision requirements.
 */
public class DashboardDtos {

    // ------------------------------------------------------------------ //
    //  Shared primitives                                                  //
    // ------------------------------------------------------------------ //

    /** A single (label, numeric value) pair — used in bar/pie charts. */
    public record LabeledValue(String label, double value) {}

    /** One point on a time-series — month in "YYYY-MM" format. */
    public record MonthPoint(String month, double value) {}

    // ------------------------------------------------------------------ //
    //  Headcount trend (GET /api/dashboards/headcount-trend)             //
    // ------------------------------------------------------------------ //

    /**
     * 12-month series of active-employee headcount, hires and leavers
     * (terminations effective in that month).
     */
    public record HeadcountTrend(
            List<MonthPoint> headcount,
            List<MonthPoint> hires,
            List<MonthPoint> leavers,
            List<LabeledValue> byDepartment,
            List<LabeledValue> byStatus
    ) {}

    // ------------------------------------------------------------------ //
    //  Payroll trend (GET /api/dashboards/payroll-trend)                 //
    // ------------------------------------------------------------------ //

    public record PayrollMonthPoint(
            String month,
            double gross,
            double net,
            double tax,
            int employeeCount
    ) {}

    public record PayrollTrend(
            List<PayrollMonthPoint> monthly,
            double totalGrossYtd,
            double totalNetYtd,
            double totalTaxYtd
    ) {}

    // ------------------------------------------------------------------ //
    //  Turnover dashboard (GET /api/dashboards/turnover)                 //
    // ------------------------------------------------------------------ //

    public record TurnoverDashboard(
            int year,
            int terminationsYtd,
            BigDecimal attritionRatePercent,
            double avgTenureMonths,
            List<MonthPoint> monthlyAttritionRate,
            List<LabeledValue> byReason
    ) {}

    // ------------------------------------------------------------------ //
    //  Manager team dashboard (GET /api/dashboards/manager-team)         //
    // ------------------------------------------------------------------ //

    public record TeamMemberRow(
            String employeeNo,
            String firstName,
            String lastName,
            String positionTitle,
            String employmentStatus
    ) {}

    public record UpcomingLeaveRow(
            String employeeName,
            String leaveTypeCode,
            String startDate,
            String endDate,
            double totalDays
    ) {}

    public record ManagerTeamDashboard(
            int teamSize,
            int pendingApprovals,
            List<LabeledValue> statusBreakdown,
            List<TeamMemberRow> members,
            List<UpcomingLeaveRow> upcomingLeave
    ) {}

    // ------------------------------------------------------------------ //
    //  Executive dashboard (GET /api/dashboards/executive)               //
    // ------------------------------------------------------------------ //

    public record ExecutiveDashboard(
            int totalHeadcount,
            double currentMonthPayrollCost,
            BigDecimal attritionRateYtd,
            double trainingCompliancePercent,
            int openVacancies,
            int pendingApprovals,
            List<MonthPoint> headcountTrend,
            List<PayrollMonthPoint> payrollTrend
    ) {}
}
