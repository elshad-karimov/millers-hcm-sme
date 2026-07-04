package az.millers.hcm.reporting.export;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import az.millers.hcm.reporting.api.dto.ReportDtos.AttendanceEmployeeRow;
import az.millers.hcm.reporting.api.dto.ReportDtos.AttendanceReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.AttritionReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.HeadcountReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.LabeledCount;
import az.millers.hcm.reporting.api.dto.ReportDtos.LeaveAtRiskRow;
import az.millers.hcm.reporting.api.dto.ReportDtos.LeaveReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.LeaveTypeUsage;
import az.millers.hcm.reporting.api.dto.ReportDtos.MandatoryCourseRow;
import az.millers.hcm.reporting.api.dto.ReportDtos.NonCompliantEmployee;
import az.millers.hcm.reporting.api.dto.ReportDtos.PayrollPeriodRow;
import az.millers.hcm.reporting.api.dto.ReportDtos.PayrollSummaryReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.PerformanceCycleSummary;
import az.millers.hcm.reporting.api.dto.ReportDtos.PerformanceReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.RatingBucketRow;
import az.millers.hcm.reporting.api.dto.ReportDtos.RecruitmentReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.TrainingReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.VacancyFunnelRow;
import az.millers.hcm.reporting.domain.ReportType;
import az.millers.hcm.reporting.service.ReportService;

/**
 * Glue between the typed report DTOs ({@link ReportService}) and the
 * format-agnostic {@link ReportSection} model. Each switch arm knows how
 * to flatten the report into headline summary stats plus one or more
 * breakdown tables — the actual PDF/XLSX rendering then just walks the
 * sections without caring which report it came from.
 */
@Component
public class ReportSectionBuilder {

    private final ReportService reportService;

    public ReportSectionBuilder(ReportService reportService) {
        this.reportService = reportService;
    }

    public List<ReportSection> build(ReportType type, Map<String, Object> params) {
        return switch (type) {
            case HEADCOUNT   -> headcount();
            case ATTRITION   -> attrition(intParam(params, "year"));
            case PAYROLL     -> payroll(intParam(params, "year"));
            case LEAVE       -> leave(intParam(params, "year"));
            case ATTENDANCE  -> attendance(dateParam(params, "from"), dateParam(params, "to"));
            case TRAINING    -> training();
            case PERFORMANCE -> performance(uuidParam(params, "cycleId"));
            case RECRUITMENT -> recruitment();
        };
    }

    // ====================================================================

    private List<ReportSection> headcount() {
        HeadcountReport r = reportService.headcount();
        ReportSection s = new ReportSection("Headcount")
                .withSummary("Total employees", r.totalEmployees())
                .withSummary("Active", r.activeEmployees())
                .withSummary("On probation", r.onProbation())
                .withSummary("On leave / business trip", r.onLeave())
                .withSummary("Terminated YTD", r.terminatedYearToDate())
                .withSummary("Attrition rate (%)", r.attritionRatePercent());

        s.withTable(labeledTable("By status", "Status", r.byStatus()));
        s.withTable(labeledTable("By department", "Department", r.byDepartment()));
        s.withTable(labeledTable("Hires (last 12 months)", "Month", r.hiresLast12Months()));
        s.withTable(labeledTable("Leavers (last 12 months)", "Month", r.leaversLast12Months()));
        return List.of(s);
    }

    private List<ReportSection> attrition(Integer year) {
        AttritionReport r = reportService.attrition(year);
        ReportSection s = new ReportSection("Attrition " + r.year())
                .withSummary("Terminations total", r.terminationsTotal())
                .withSummary("Attrition rate (%)", r.attritionRatePercent())
                .withSummary("Total settlement payout",
                        r.totalSettlementPayout() + " " + r.currency());
        s.withTable(labeledTable("By reason", "Reason", r.byReason()));
        s.withTable(labeledTable("By month", "Month", r.byMonth()));
        return List.of(s);
    }

    private List<ReportSection> payroll(Integer year) {
        PayrollSummaryReport r = reportService.payrollSummary(year);
        ReportSection s = new ReportSection("Payroll summary " + r.year())
                .withSummary("Gross YTD", r.totalGrossYtd())
                .withSummary("Net YTD", r.totalNetYtd())
                .withSummary("Tax YTD", r.totalTaxYtd())
                .withSummary("Other deductions YTD", r.totalDeductionsYtd())
                .withSummary("Runs closed", r.runsClosed())
                .withSummary("Runs in progress", r.runsInProgress());

        ReportTable runsTable = new ReportTable("Runs",
                List.of("Run #", "Period", "Status", "Employees",
                        "Gross", "Tax", "Deductions", "Net", "Currency"));
        for (PayrollPeriodRow row : r.runs()) {
            runsTable.addRow(
                    row.runNo(),
                    row.year() + "-" + String.format("%02d", row.month()),
                    row.status(),
                    row.employeeCount(),
                    row.totalGross(), row.totalTax(),
                    row.totalDeductions(), row.totalNet(),
                    row.currency());
        }
        s.withTable(runsTable);
        return List.of(s);
    }

    private List<ReportSection> leave(Integer year) {
        LeaveReport r = reportService.leaveUsage(year);
        ReportSection s = new ReportSection("Leave usage " + r.year());

        ReportTable byType = new ReportTable("By leave type",
                List.of("Code", "Name", "Employees",
                        "Entitlement", "Used", "Reserved", "Remaining"));
        for (LeaveTypeUsage t : r.byType()) {
            byType.addRow(t.leaveTypeCode(), t.leaveTypeName(), t.employeeCount(),
                    t.entitlementSum(), t.usedSum(), t.reservedSum(), t.remainingSum());
        }
        s.withTable(byType);

        s.withTable(leaveAtRisk("Overdrawn (remaining < 0)", r.overdrawn()));
        s.withTable(leaveAtRisk("High annual-leave balance (≥ 80%)", r.highBalanceAtRisk()));
        return List.of(s);
    }

    private ReportTable leaveAtRisk(String title, List<LeaveAtRiskRow> rows) {
        ReportTable t = new ReportTable(title,
                List.of("Employee #", "Name", "Leave", "Remaining", "Entitlement"));
        for (LeaveAtRiskRow row : rows) {
            t.addRow(row.employeeNo(), row.fullName(), row.leaveTypeCode(),
                    row.remaining(), row.entitlement());
        }
        return t;
    }

    private List<ReportSection> attendance(LocalDate from, LocalDate to) {
        AttendanceReport r = reportService.attendance(from, to);
        ReportSection s = new ReportSection(
                "Attendance " + r.from() + " — " + r.to())
                .withSummary("Total worked hours", r.totalWorkedHours())
                .withSummary("Late minutes", r.totalLateMinutes())
                .withSummary("Early-leave minutes", r.totalEarlyMinutes())
                .withSummary("Overtime minutes", r.totalOvertimeMinutes())
                .withSummary("Absent days", r.totalAbsentDays());

        ReportTable byEmp = new ReportTable("By employee",
                List.of("Employee #", "Name", "Worked (h)", "Work days",
                        "Late (min)", "Early (min)", "OT (min)", "Absent days"));
        for (AttendanceEmployeeRow row : r.byEmployee()) {
            byEmp.addRow(row.employeeNo(), row.fullName(),
                    row.workedHours(), row.workDays(),
                    row.lateMinutes(), row.earlyMinutes(),
                    row.overtimeMinutes(), row.absentDays());
        }
        s.withTable(byEmp);
        return List.of(s);
    }

    private List<ReportSection> training() {
        TrainingReport r = reportService.trainingCompliance();
        ReportSection s = new ReportSection("Training compliance")
                .withSummary("Mandatory courses", r.mandatoryCourses())
                .withSummary("Active employees", r.employeesActive())
                .withSummary("Overall compliance (%)", r.overallCompliancePercent());

        ReportTable perCourse = new ReportTable("Per mandatory course",
                List.of("Course #", "Code", "Title",
                        "Enrolled", "Passed", "Failed", "In progress", "Completion %"));
        for (MandatoryCourseRow row : r.perCourse()) {
            perCourse.addRow(row.courseNo(), row.code(), row.title(),
                    row.enrolled(), row.passed(), row.failed(),
                    row.inProgress(), row.completionRatePercent());
        }
        s.withTable(perCourse);

        ReportTable non = new ReportTable("Non-compliant employees",
                List.of("Employee #", "Name", "Course", "Status"));
        for (NonCompliantEmployee row : r.nonCompliant()) {
            non.addRow(row.employeeNo(), row.fullName(), row.courseTitle(), row.reason());
        }
        s.withTable(non);
        return List.of(s);
    }

    private List<ReportSection> performance(UUID cycleId) {
        PerformanceReport r = reportService.performance(cycleId);
        List<ReportSection> out = new ArrayList<>();
        for (PerformanceCycleSummary c : r.cycles()) {
            ReportSection s = new ReportSection(
                    "Performance — " + c.cycleCode() + " (" + c.cycleName() + ")")
                    .withSummary("Reviews total", c.reviewsTotal())
                    .withSummary("Reviews completed", c.reviewsCompleted())
                    .withSummary("Average final rating",
                            c.averageFinalRating() == null ? "—" : c.averageFinalRating());

            ReportTable dist = new ReportTable("Rating distribution",
                    List.of("Bucket", "Count"));
            for (RatingBucketRow b : c.distribution()) {
                dist.addRow(b.bucket(), b.count());
            }
            s.withTable(dist);

            s.withTable(labeledTable("Recommendation breakdown",
                    "Recommendation", c.recommendationBreakdown()));
            out.add(s);
        }
        if (out.isEmpty()) {
            out.add(new ReportSection("Performance")
                    .withSummary("Cycles found", 0));
        }
        return out;
    }

    private List<ReportSection> recruitment() {
        RecruitmentReport r = reportService.recruitment();
        ReportSection s = new ReportSection("Recruitment funnel")
                .withSummary("Vacancies open", r.vacanciesOpen())
                .withSummary("Vacancies filled", r.vacanciesFilled())
                .withSummary("Candidate pool", r.candidatesPool())
                .withSummary("Applications in progress", r.applicationsInProgress())
                .withSummary("Avg time-to-hire (days)", r.averageTimeToHireDays());

        s.withTable(labeledTable("Active applications by stage",
                "Stage", r.applicationsByStage()));

        ReportTable byVacancy = new ReportTable("Per vacancy",
                List.of("Vacancy #", "Title", "Status",
                        "Openings", "Applications", "Hires", "Avg TTH (days)"));
        for (VacancyFunnelRow row : r.byVacancy()) {
            byVacancy.addRow(row.vacancyNo(), row.title(), row.status(),
                    row.openings(), row.applicationsTotal(),
                    row.hires(), row.averageTimeToHireDays());
        }
        s.withTable(byVacancy);
        return List.of(s);
    }

    // ====================================================================

    private ReportTable labeledTable(String title, String label, List<LabeledCount> rows) {
        ReportTable t = new ReportTable(title, List.of(label, "Count"));
        for (LabeledCount r : rows) {
            t.addRow(r.label(), r.count());
        }
        return t;
    }

    private static Integer intParam(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object v = params.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static LocalDate dateParam(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object v = params.get(key);
        if (v == null) return null;
        try {
            return LocalDate.parse(v.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static UUID uuidParam(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object v = params.get(key);
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString());
        } catch (Exception ignored) {
            return null;
        }
    }
}
