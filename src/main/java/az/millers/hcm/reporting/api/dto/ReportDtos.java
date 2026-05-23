package az.millers.hcm.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight DTOs for the reporting endpoints. Kept in one file because
 * each report has its own bespoke shape and there's no behaviour worth
 * spreading across files.
 */
public final class ReportDtos {

    private ReportDtos() {}

    // ---------- Common building blocks ----------

    public record LabeledCount(String label, long count) {}

    public record LabeledAmount(String label, BigDecimal amount, String currency) {}

    public record EmployeeRow(UUID id, String employeeNo, String fullName) {}

    // ---------- Headcount (PRD 10.x) ----------

    public record HeadcountReport(
            long totalEmployees,
            long activeEmployees,
            long onProbation,
            long onLeave,
            long terminatedYearToDate,
            BigDecimal attritionRatePercent,
            List<LabeledCount> byStatus,
            List<LabeledCount> byDepartment,
            List<LabeledCount> hiresLast12Months,         // YYYY-MM → count
            List<LabeledCount> leaversLast12Months) {}    // YYYY-MM → count

    // ---------- Attrition (PRD 10.x) ----------

    public record AttritionReport(
            int year,
            long terminationsTotal,
            BigDecimal attritionRatePercent,
            List<LabeledCount> byReason,
            List<LabeledCount> byMonth,
            BigDecimal totalSettlementPayout,
            String currency) {}

    // ---------- Payroll (PRD 10.x) ----------

    public record PayrollPeriodRow(
            UUID runId,
            String runNo,
            int year,
            int month,
            String status,
            BigDecimal totalGross,
            BigDecimal totalNet,
            BigDecimal totalTax,
            BigDecimal totalDeductions,
            int employeeCount,
            String currency) {}

    public record PayrollSummaryReport(
            int year,
            BigDecimal totalGrossYtd,
            BigDecimal totalNetYtd,
            BigDecimal totalTaxYtd,
            BigDecimal totalDeductionsYtd,
            long runsClosed,
            long runsInProgress,
            List<PayrollPeriodRow> runs) {}

    // ---------- Leave usage (PRD 10.x) ----------

    public record LeaveTypeUsage(
            String leaveTypeCode,
            String leaveTypeName,
            BigDecimal entitlementSum,
            BigDecimal usedSum,
            BigDecimal reservedSum,
            BigDecimal remainingSum,
            long employeeCount) {}

    public record LeaveAtRiskRow(
            UUID employeeId,
            String employeeNo,
            String fullName,
            String leaveTypeCode,
            BigDecimal remaining,
            BigDecimal entitlement) {}

    public record LeaveReport(
            int year,
            List<LeaveTypeUsage> byType,
            List<LeaveAtRiskRow> overdrawn,                // remaining < 0
            List<LeaveAtRiskRow> highBalanceAtRisk) {}     // > 80% of entitlement still remaining

    // ---------- Attendance (PRD 10.x) ----------

    public record AttendanceEmployeeRow(
            UUID employeeId,
            String employeeNo,
            String fullName,
            BigDecimal workedHours,
            BigDecimal lateMinutes,
            BigDecimal earlyMinutes,
            BigDecimal overtimeMinutes,
            long workDays,
            long absentDays) {}

    public record AttendanceReport(
            LocalDate from,
            LocalDate to,
            BigDecimal totalWorkedHours,
            BigDecimal totalLateMinutes,
            BigDecimal totalEarlyMinutes,
            BigDecimal totalOvertimeMinutes,
            long totalAbsentDays,
            List<AttendanceEmployeeRow> byEmployee) {}

    // ---------- Training compliance (PRD 10.x) ----------

    public record MandatoryCourseRow(
            UUID courseId,
            String courseNo,
            String code,
            String title,
            long enrolled,
            long passed,
            long failed,
            long inProgress,
            BigDecimal completionRatePercent) {}

    public record NonCompliantEmployee(
            UUID employeeId,
            String employeeNo,
            String fullName,
            UUID courseId,
            String courseTitle,
            String reason) {}

    public record TrainingReport(
            long mandatoryCourses,
            long employeesActive,
            BigDecimal overallCompliancePercent,
            List<MandatoryCourseRow> perCourse,
            List<NonCompliantEmployee> nonCompliant) {}

    // ---------- Performance distribution (PRD 10.x) ----------

    public record RatingBucketRow(BigDecimal bucket, long count) {}

    public record PerformanceCycleSummary(
            UUID cycleId,
            String cycleCode,
            String cycleName,
            long reviewsTotal,
            long reviewsCompleted,
            BigDecimal averageFinalRating,
            List<RatingBucketRow> distribution,
            List<LabeledCount> recommendationBreakdown) {}

    public record PerformanceReport(
            List<PerformanceCycleSummary> cycles) {}

    // ---------- Recruitment funnel (PRD 10.x) ----------

    public record VacancyFunnelRow(
            UUID vacancyId,
            String vacancyNo,
            String title,
            String status,
            int openings,
            long applicationsTotal,
            long hires,
            BigDecimal averageTimeToHireDays) {}

    public record RecruitmentReport(
            long vacanciesOpen,
            long vacanciesFilled,
            long candidatesPool,
            long applicationsInProgress,
            BigDecimal averageTimeToHireDays,
            List<LabeledCount> applicationsByStage,
            List<VacancyFunnelRow> byVacancy) {}
}
