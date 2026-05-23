package az.millers.hcm.reporting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Read-only aggregation service for cross-module reports (PRD Section 10).
 *
 * <p>Implemented as native SQL via {@link NamedParameterJdbcTemplate} —
 * Reports are inherently aggregate-heavy and join across module schemas
 * (core_hr, lifecycle, payroll, leave_mgmt, attendance, learning,
 * performance, recruitment); going through JPA would mean a fan of
 * cross-schema repos that don't really model anything. Keeping it in
 * one service makes the SQL auditable in one place.
 */
@Service
public class ReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AccessScopeService accessScope;

    public ReportService(NamedParameterJdbcTemplate jdbc, AccessScopeService accessScope) {
        this.jdbc = jdbc;
        this.accessScope = accessScope;
    }

    /**
     * Returns {@code " AND <col> IN (:scopeIds) "} when the caller is
     * scope-restricted (DEPARTMENT_MANAGER / EMPLOYEE), otherwise an
     * empty string. Bind the caller's scope as {@code :scopeIds} on the
     * matching {@link MapSqlParameterSource} when this returns non-empty.
     *
     * <p>Used by each aggregation query to narrow rows to the caller's
     * accessible employee set without forcing a fan-in of pre-computed
     * scope queries (PRD 14.9 — extended ABAC on reports).
     */
    private static String scopeClause(Set<UUID> scope, String empColumn) {
        return scope == null ? "" : " AND " + empColumn + " IN (:scopeIds) ";
    }

    /** Bind the scope set under {@code :scopeIds} when the caller is restricted. */
    private static MapSqlParameterSource withScope(MapSqlParameterSource params, Set<UUID> scope) {
        if (scope != null) params.addValue("scopeIds", scope);
        return params;
    }

    // ====================================================================
    //  HEADCOUNT
    // ====================================================================

    @Transactional(readOnly = true)
    public HeadcountReport headcount() {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope != null && scope.isEmpty()) {
            return new HeadcountReport(0, 0, 0, 0, 0,
                    BigDecimal.ZERO, List.of(), List.of(), List.of(), List.of());
        }
        int currentYear = Year.now().getValue();
        Map<String, Long> byStatus = new HashMap<>();
        jdbc.query(
                "SELECT employment_status, count(*) c FROM core_hr.employee "
                        + " WHERE 1=1 " + scopeClause(scope, "id")
                        + " GROUP BY employment_status",
                withScope(new MapSqlParameterSource(), scope),
                (rs, i) -> { byStatus.put(rs.getString(1), rs.getLong(2)); return null; });

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long active = byStatus.getOrDefault("ACTIVE", 0L);
        long probation = byStatus.getOrDefault("ON_PROBATION", 0L);
        long onLeave = byStatus.getOrDefault("ON_LEAVE", 0L) + byStatus.getOrDefault("ON_BUSINESS_TRIP", 0L);

        Long terminatedYtd = jdbc.queryForObject(
                "SELECT count(*) FROM lifecycle.termination_request "
                        + " WHERE status = 'PROCESSED' AND date_part('year', processed_at) = :year "
                        + scopeClause(scope, "employee_id"),
                withScope(new MapSqlParameterSource("year", currentYear), scope), Long.class);
        if (terminatedYtd == null) terminatedYtd = 0L;

        // Attrition = terminations in period / average active headcount × 100. We
        // approximate "average" with current active+terminated count so the metric
        // stays sensible on a small dataset.
        long denom = Math.max(1L, active + probation + onLeave + terminatedYtd);
        BigDecimal attrition = BigDecimal.valueOf(terminatedYtd)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denom), 2, RoundingMode.HALF_UP);

        List<LabeledCount> statusList = byStatus.entrySet().stream()
                .map(e -> new LabeledCount(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();

        List<LabeledCount> byDept = jdbc.query(
                "SELECT COALESCE(department_name, '— unassigned') AS d, count(*) c "
                        + " FROM core_hr.employee WHERE employment_status <> 'TERMINATED' "
                        + scopeClause(scope, "id")
                        + " GROUP BY d ORDER BY c DESC",
                withScope(new MapSqlParameterSource(), scope),
                (rs, i) -> new LabeledCount(rs.getString(1), rs.getLong(2)));

        List<LabeledCount> hires = jdbc.query(
                "SELECT to_char(hire_date, 'YYYY-MM') AS m, count(*) c "
                        + " FROM core_hr.employee "
                        + " WHERE hire_date >= (current_date - interval '12 months') "
                        + scopeClause(scope, "id")
                        + " GROUP BY m ORDER BY m",
                withScope(new MapSqlParameterSource(), scope),
                (rs, i) -> new LabeledCount(rs.getString(1), rs.getLong(2)));

        List<LabeledCount> leavers = jdbc.query(
                "SELECT to_char(effective_date, 'YYYY-MM') AS m, count(*) c "
                        + " FROM lifecycle.termination_request "
                        + " WHERE status = 'PROCESSED' "
                        + "   AND effective_date >= (current_date - interval '12 months') "
                        + scopeClause(scope, "employee_id")
                        + " GROUP BY m ORDER BY m",
                withScope(new MapSqlParameterSource(), scope),
                (rs, i) -> new LabeledCount(rs.getString(1), rs.getLong(2)));

        return new HeadcountReport(total, active, probation, onLeave, terminatedYtd,
                attrition, statusList, byDept, hires, leavers);
    }

    // ====================================================================
    //  ATTRITION
    // ====================================================================

    @Transactional(readOnly = true)
    public AttritionReport attrition(Integer year) {
        int y = year == null ? Year.now().getValue() : year;
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope != null && scope.isEmpty()) {
            return new AttritionReport(y, 0L, BigDecimal.ZERO, List.of(), List.of(),
                    BigDecimal.ZERO, "AZN");
        }

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM lifecycle.termination_request "
                        + " WHERE status = 'PROCESSED' AND date_part('year', processed_at) = :y "
                        + scopeClause(scope, "employee_id"),
                withScope(new MapSqlParameterSource("y", y), scope), Long.class);
        if (total == null) total = 0L;

        Long activeNow = jdbc.queryForObject(
                "SELECT count(*) FROM core_hr.employee WHERE employment_status <> 'TERMINATED' "
                        + scopeClause(scope, "id"),
                withScope(new MapSqlParameterSource(), scope), Long.class);
        if (activeNow == null) activeNow = 0L;

        BigDecimal rate = activeNow + total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(activeNow + total), 2, RoundingMode.HALF_UP);

        List<LabeledCount> byReason = jdbc.query(
                "SELECT reason_code, count(*) c FROM lifecycle.termination_request "
                        + " WHERE status = 'PROCESSED' AND date_part('year', processed_at) = :y "
                        + scopeClause(scope, "employee_id")
                        + " GROUP BY reason_code ORDER BY c DESC",
                withScope(new MapSqlParameterSource("y", y), scope),
                (rs, i) -> new LabeledCount(rs.getString(1), rs.getLong(2)));

        List<LabeledCount> byMonth = jdbc.query(
                "SELECT to_char(processed_at, 'YYYY-MM') m, count(*) c FROM lifecycle.termination_request "
                        + " WHERE status = 'PROCESSED' AND date_part('year', processed_at) = :y "
                        + scopeClause(scope, "employee_id")
                        + " GROUP BY m ORDER BY m",
                withScope(new MapSqlParameterSource("y", y), scope),
                (rs, i) -> new LabeledCount(rs.getString(1), rs.getLong(2)));

        BigDecimal totalPayout = jdbc.queryForObject(
                "SELECT COALESCE(sum(final_settlement_amount), 0) FROM lifecycle.termination_request "
                        + " WHERE status = 'PROCESSED' AND date_part('year', processed_at) = :y "
                        + scopeClause(scope, "employee_id"),
                withScope(new MapSqlParameterSource("y", y), scope), BigDecimal.class);

        String currency = jdbc.query(
                "SELECT currency FROM lifecycle.termination_request "
                        + " WHERE status = 'PROCESSED' AND currency IS NOT NULL "
                        + scopeClause(scope, "employee_id")
                        + " LIMIT 1",
                withScope(new MapSqlParameterSource(), scope),
                (rs, i) -> rs.getString(1))
                .stream().findFirst().orElse("AZN");

        return new AttritionReport(y, total, rate, byReason, byMonth,
                totalPayout == null ? BigDecimal.ZERO : totalPayout, currency);
    }

    // ====================================================================
    //  PAYROLL SUMMARY
    // ====================================================================

    @Transactional(readOnly = true)
    public PayrollSummaryReport payrollSummary(Integer year) {
        int y = year == null ? Year.now().getValue() : year;
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope != null && scope.isEmpty()) {
            return new PayrollSummaryReport(y, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, List.of());
        }
        if (scope != null) {
            // Scoped caller: re-aggregate from payroll_result per-employee
            // rows filtered to the caller's scope set, then group by run.
            // HAVING count(pr.id) > 0 hides runs where the scoped team had
            // no payroll_result rows — otherwise the manager would see
            // every run with all-zero totals, which is misleading (PRD 14.9).
            return payrollSummaryScoped(y, scope);
        }

        List<PayrollPeriodRow> runs = jdbc.query(
                "SELECT id, run_no, period_year, period_month, status, "
                        + "       total_gross, total_net, total_income_tax, "
                        + "       (total_dsmf_employee + total_mmi_employee + total_unempl_employee) AS deductions, "
                        + "       employee_count, currency "
                        + "  FROM payroll.payroll_run "
                        + " WHERE period_year = :y "
                        + " ORDER BY period_month",
                new MapSqlParameterSource("y", y),
                (rs, i) -> new PayrollPeriodRow(
                        (UUID) rs.getObject("id"),
                        rs.getString("run_no"),
                        rs.getInt("period_year"),
                        rs.getInt("period_month"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_gross"),
                        rs.getBigDecimal("total_net"),
                        rs.getBigDecimal("total_income_tax"),
                        rs.getBigDecimal("deductions"),
                        rs.getInt("employee_count"),
                        rs.getString("currency")));

        BigDecimal grossYtd = BigDecimal.ZERO, netYtd = BigDecimal.ZERO,
                taxYtd = BigDecimal.ZERO, dedYtd = BigDecimal.ZERO;
        long closed = 0, inProgress = 0;
        for (PayrollPeriodRow r : runs) {
            if (r.totalGross() != null) grossYtd = grossYtd.add(r.totalGross());
            if (r.totalNet() != null) netYtd = netYtd.add(r.totalNet());
            if (r.totalTax() != null) taxYtd = taxYtd.add(r.totalTax());
            if (r.totalDeductions() != null) dedYtd = dedYtd.add(r.totalDeductions());
            if ("CLOSED".equals(r.status()) || "PAID".equals(r.status())) closed++;
            else inProgress++;
        }
        return new PayrollSummaryReport(y, grossYtd, netYtd, taxYtd, dedYtd, closed, inProgress, runs);
    }

    /**
     * Scoped payroll aggregation — sums per-employee {@code payroll_result}
     * rows filtered by the caller's scope, grouped by run. Returns only
     * the runs where the scoped team actually had results so the manager
     * doesn't see every org-wide run with zero-filled totals (PRD 14.9).
     */
    private PayrollSummaryReport payrollSummaryScoped(int y, Set<UUID> scope) {
        List<PayrollPeriodRow> runs = jdbc.query(
                "SELECT r.id, r.run_no, r.period_year, r.period_month, r.status, r.currency, "
                        + "       COALESCE(SUM(pr.gross_amount), 0)                                                   AS gross, "
                        + "       COALESCE(SUM(pr.net_amount),   0)                                                   AS net, "
                        + "       COALESCE(SUM(pr.income_tax),   0)                                                   AS tax, "
                        + "       COALESCE(SUM(pr.dsmf_employee + pr.mmi_employee + pr.unempl_employee), 0)            AS deductions, "
                        + "       COUNT(DISTINCT pr.employee_id)                                                       AS emp_count "
                        + "  FROM payroll.payroll_run r "
                        + "  LEFT JOIN payroll.payroll_result pr "
                        + "         ON pr.run_id = r.id AND pr.employee_id IN (:scopeIds) "
                        + " WHERE r.period_year = :y "
                        + " GROUP BY r.id, r.run_no, r.period_year, r.period_month, r.status, r.currency "
                        + " HAVING COUNT(pr.id) > 0 "
                        + " ORDER BY r.period_month",
                new MapSqlParameterSource("y", y).addValue("scopeIds", scope),
                (rs, i) -> new PayrollPeriodRow(
                        (UUID) rs.getObject("id"),
                        rs.getString("run_no"),
                        rs.getInt("period_year"),
                        rs.getInt("period_month"),
                        rs.getString("status"),
                        rs.getBigDecimal("gross"),
                        rs.getBigDecimal("net"),
                        rs.getBigDecimal("tax"),
                        rs.getBigDecimal("deductions"),
                        rs.getInt("emp_count"),
                        rs.getString("currency")));

        BigDecimal grossYtd = BigDecimal.ZERO, netYtd = BigDecimal.ZERO,
                taxYtd = BigDecimal.ZERO, dedYtd = BigDecimal.ZERO;
        long closed = 0, inProgress = 0;
        for (PayrollPeriodRow r : runs) {
            if (r.totalGross() != null) grossYtd = grossYtd.add(r.totalGross());
            if (r.totalNet() != null) netYtd = netYtd.add(r.totalNet());
            if (r.totalTax() != null) taxYtd = taxYtd.add(r.totalTax());
            if (r.totalDeductions() != null) dedYtd = dedYtd.add(r.totalDeductions());
            if ("CLOSED".equals(r.status()) || "PAID".equals(r.status())) closed++;
            else inProgress++;
        }
        return new PayrollSummaryReport(y, grossYtd, netYtd, taxYtd, dedYtd, closed, inProgress, runs);
    }

    // ====================================================================
    //  LEAVE USAGE
    // ====================================================================

    @Transactional(readOnly = true)
    public LeaveReport leaveUsage(Integer year) {
        int y = year == null ? Year.now().getValue() : year;
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope != null && scope.isEmpty()) {
            return new LeaveReport(y, List.of(), List.of(), List.of());
        }

        // LEFT JOIN: pre-filter the leave_balance rows in the ON clause so
        // each type still shows up even when the caller's scope has no
        // balance rows for it (one row per leave_type, zeros where empty).
        List<LeaveTypeUsage> byType = jdbc.query(
                "SELECT t.code, t.name, "
                        + "       COALESCE(sum(b.entitlement_days), 0) AS ent, "
                        + "       COALESCE(sum(b.used_days), 0)        AS used, "
                        + "       COALESCE(sum(b.reserved_days), 0)    AS res, "
                        + "       COALESCE(sum(b.entitlement_days + b.carried_forward_days + b.adjustment_days "
                        + "                   - b.used_days - b.reserved_days), 0) AS remaining, "
                        + "       count(DISTINCT b.employee_id) AS emp "
                        + "  FROM leave_mgmt.leave_type t "
                        + "  LEFT JOIN leave_mgmt.leave_balance b "
                        + "         ON b.leave_type_id = t.id AND b.year = :y "
                        + (scope == null ? "" : "        AND b.employee_id IN (:scopeIds) ")
                        + " WHERE t.active = true "
                        + " GROUP BY t.code, t.name "
                        + " ORDER BY t.name",
                withScope(new MapSqlParameterSource("y", y), scope),
                (rs, i) -> new LeaveTypeUsage(
                        rs.getString("code"), rs.getString("name"),
                        rs.getBigDecimal("ent"), rs.getBigDecimal("used"),
                        rs.getBigDecimal("res"), rs.getBigDecimal("remaining"),
                        rs.getLong("emp")));

        List<LeaveAtRiskRow> overdrawn = jdbc.query(
                "SELECT b.employee_id, e.employee_no, "
                        + "       e.first_name || ' ' || e.last_name AS full_name, "
                        + "       t.code AS leave_code, "
                        + "       (b.entitlement_days + b.carried_forward_days + b.adjustment_days "
                        + "        - b.used_days - b.reserved_days) AS remaining, "
                        + "       b.entitlement_days "
                        + "  FROM leave_mgmt.leave_balance b "
                        + "  JOIN core_hr.employee e ON e.id = b.employee_id "
                        + "  JOIN leave_mgmt.leave_type t ON t.id = b.leave_type_id "
                        + " WHERE b.year = :y "
                        + "   AND (b.entitlement_days + b.carried_forward_days + b.adjustment_days "
                        + "        - b.used_days - b.reserved_days) < 0 "
                        + scopeClause(scope, "b.employee_id")
                        + " ORDER BY remaining ASC LIMIT 50",
                withScope(new MapSqlParameterSource("y", y), scope),
                (rs, i) -> new LeaveAtRiskRow(
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getString("leave_code"),
                        rs.getBigDecimal("remaining"),
                        rs.getBigDecimal("entitlement_days")));

        List<LeaveAtRiskRow> highBalance = jdbc.query(
                "SELECT b.employee_id, e.employee_no, "
                        + "       e.first_name || ' ' || e.last_name AS full_name, "
                        + "       t.code AS leave_code, "
                        + "       (b.entitlement_days + b.carried_forward_days + b.adjustment_days "
                        + "        - b.used_days - b.reserved_days) AS remaining, "
                        + "       b.entitlement_days "
                        + "  FROM leave_mgmt.leave_balance b "
                        + "  JOIN core_hr.employee e ON e.id = b.employee_id "
                        + "  JOIN leave_mgmt.leave_type t ON t.id = b.leave_type_id "
                        + " WHERE b.year = :y "
                        + "   AND b.entitlement_days > 0 "
                        + "   AND t.code = 'ANNUAL' "
                        + "   AND (b.entitlement_days + b.carried_forward_days + b.adjustment_days "
                        + "        - b.used_days - b.reserved_days) >= 0.8 * b.entitlement_days "
                        + scopeClause(scope, "b.employee_id")
                        + " ORDER BY remaining DESC LIMIT 50",
                withScope(new MapSqlParameterSource("y", y), scope),
                (rs, i) -> new LeaveAtRiskRow(
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getString("leave_code"),
                        rs.getBigDecimal("remaining"),
                        rs.getBigDecimal("entitlement_days")));

        return new LeaveReport(y, byType, overdrawn, highBalance);
    }

    // ====================================================================
    //  ATTENDANCE
    // ====================================================================

    @Transactional(readOnly = true)
    public AttendanceReport attendance(LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = from.plusMonths(1).minusDays(1);
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope != null && scope.isEmpty()) {
            return new AttendanceReport(from, to, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0L, List.of());
        }

        MapSqlParameterSource p = withScope(
                new MapSqlParameterSource().addValue("from", from).addValue("to", to),
                scope);

        List<AttendanceEmployeeRow> rows = jdbc.query(
                "SELECT s.employee_id, e.employee_no, "
                        + "       e.first_name || ' ' || e.last_name AS full_name, "
                        + "       COALESCE(sum(s.worked_minutes), 0) / 60.0   AS worked_h, "
                        + "       COALESCE(sum(s.late_minutes), 0)             AS late_m, "
                        + "       COALESCE(sum(s.early_minutes), 0)            AS early_m, "
                        + "       COALESCE(sum(s.overtime_minutes), 0)         AS ot_m, "
                        + "       count(*) FILTER (WHERE s.worked_minutes > 0) AS work_days, "
                        + "       count(*) FILTER (WHERE s.status = 'ABSENT')  AS absent_days "
                        + "  FROM attendance.daily_summary s "
                        + "  JOIN core_hr.employee e ON e.id = s.employee_id "
                        + " WHERE s.work_date BETWEEN :from AND :to "
                        + scopeClause(scope, "s.employee_id")
                        + " GROUP BY s.employee_id, e.employee_no, full_name "
                        + " ORDER BY late_m DESC NULLS LAST, worked_h DESC",
                p,
                (rs, i) -> new AttendanceEmployeeRow(
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getBigDecimal("worked_h"),
                        rs.getBigDecimal("late_m"),
                        rs.getBigDecimal("early_m"),
                        rs.getBigDecimal("ot_m"),
                        rs.getLong("work_days"),
                        rs.getLong("absent_days")));

        BigDecimal totalWorked = BigDecimal.ZERO, totalLate = BigDecimal.ZERO,
                totalEarly = BigDecimal.ZERO, totalOt = BigDecimal.ZERO;
        long totalAbsent = 0;
        for (AttendanceEmployeeRow r : rows) {
            if (r.workedHours() != null) totalWorked = totalWorked.add(r.workedHours());
            if (r.lateMinutes() != null) totalLate = totalLate.add(r.lateMinutes());
            if (r.earlyMinutes() != null) totalEarly = totalEarly.add(r.earlyMinutes());
            if (r.overtimeMinutes() != null) totalOt = totalOt.add(r.overtimeMinutes());
            totalAbsent += r.absentDays();
        }
        return new AttendanceReport(from, to, totalWorked, totalLate, totalEarly, totalOt,
                totalAbsent, rows);
    }

    // ====================================================================
    //  TRAINING COMPLIANCE
    // ====================================================================

    @Transactional(readOnly = true)
    public TrainingReport trainingCompliance() {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope != null && scope.isEmpty()) {
            return new TrainingReport(0L, 0L, BigDecimal.ZERO, List.of(), List.of());
        }

        Long mandatoryCount = jdbc.queryForObject(
                "SELECT count(*) FROM learning.course WHERE mandatory = true AND status = 'PUBLISHED'",
                new MapSqlParameterSource(), Long.class);
        if (mandatoryCount == null) mandatoryCount = 0L;

        Long activeEmployees = jdbc.queryForObject(
                "SELECT count(*) FROM core_hr.employee WHERE employment_status NOT IN ('TERMINATED') "
                        + scopeClause(scope, "id"),
                withScope(new MapSqlParameterSource(), scope), Long.class);
        if (activeEmployees == null) activeEmployees = 0L;

        // LEFT JOIN scope predicate goes in the ON clause so courses with
        // no scoped enrollments still appear (count=0).
        List<MandatoryCourseRow> perCourse = jdbc.query(
                "SELECT c.id, c.course_no, c.code, c.title, "
                        + "       count(en.id)                                          AS enrolled, "
                        + "       count(en.id) FILTER (WHERE en.status = 'PASSED')      AS passed, "
                        + "       count(en.id) FILTER (WHERE en.status = 'FAILED')      AS failed, "
                        + "       count(en.id) FILTER (WHERE en.status IN ('ENROLLED','IN_PROGRESS')) AS ip "
                        + "  FROM learning.course c "
                        + "  LEFT JOIN learning.enrollment en ON en.course_id = c.id "
                        + (scope == null ? "" : "        AND en.employee_id IN (:scopeIds) ")
                        + " WHERE c.mandatory = true AND c.status = 'PUBLISHED' "
                        + " GROUP BY c.id, c.course_no, c.code, c.title "
                        + " ORDER BY c.title",
                withScope(new MapSqlParameterSource(), scope),
                (rs, i) -> {
                    long enr = rs.getLong("enrolled");
                    long passed = rs.getLong("passed");
                    BigDecimal pct = enr == 0
                            ? BigDecimal.ZERO
                            : BigDecimal.valueOf(passed).multiply(BigDecimal.valueOf(100))
                                    .divide(BigDecimal.valueOf(enr), 2, RoundingMode.HALF_UP);
                    return new MandatoryCourseRow(
                            (UUID) rs.getObject("id"),
                            rs.getString("course_no"),
                            rs.getString("code"),
                            rs.getString("title"),
                            enr, passed,
                            rs.getLong("failed"),
                            rs.getLong("ip"),
                            pct);
                });

        // Active employees not yet PASSED on each mandatory course.
        List<NonCompliantEmployee> nonCompliant = jdbc.query(
                "SELECT e.id AS employee_id, e.employee_no, "
                        + "       e.first_name || ' ' || e.last_name AS full_name, "
                        + "       c.id AS course_id, c.title AS course_title, "
                        + "       COALESCE(en.status::text, 'NOT_ENROLLED') AS reason "
                        + "  FROM core_hr.employee e "
                        + "  CROSS JOIN learning.course c "
                        + "  LEFT JOIN learning.enrollment en "
                        + "         ON en.course_id = c.id AND en.employee_id = e.id "
                        + " WHERE c.mandatory = true AND c.status = 'PUBLISHED' "
                        + "   AND e.employment_status NOT IN ('TERMINATED') "
                        + "   AND (en.status IS NULL OR en.status <> 'PASSED') "
                        + scopeClause(scope, "e.id")
                        + " ORDER BY full_name, course_title LIMIT 200",
                withScope(new MapSqlParameterSource(), scope),
                (rs, i) -> new NonCompliantEmployee(
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        (UUID) rs.getObject("course_id"),
                        rs.getString("course_title"),
                        rs.getString("reason")));

        long required = mandatoryCount * activeEmployees;
        long completed = perCourse.stream().mapToLong(MandatoryCourseRow::passed).sum();
        BigDecimal compliance = required == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(required), 2, RoundingMode.HALF_UP);

        return new TrainingReport(mandatoryCount, activeEmployees, compliance, perCourse, nonCompliant);
    }

    // ====================================================================
    //  PERFORMANCE DISTRIBUTION
    // ====================================================================

    @Transactional(readOnly = true)
    public PerformanceReport performance(UUID cycleId) {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope != null && scope.isEmpty()) {
            return new PerformanceReport(List.of());
        }

        String cycleFilter = cycleId == null ? "" : " WHERE rc.id = :cycleId ";
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (cycleId != null) p.addValue("cycleId", cycleId);

        List<PerformanceCycleSummary> cycles = new ArrayList<>();
        // Cycle list itself is org-wide metadata (start/end dates) — scope
        // doesn't reduce which cycles exist, only the reviews counted under each.
        List<Map<String, Object>> cycleRows = jdbc.queryForList(
                "SELECT rc.id, rc.code, rc.name FROM performance.review_cycle rc "
                        + cycleFilter + " ORDER BY rc.period_start DESC",
                p);

        for (Map<String, Object> c : cycleRows) {
            UUID cid = (UUID) c.get("id");
            String code = (String) c.get("code");
            String name = (String) c.get("name");

            Map<String, Object> stats = jdbc.queryForMap(
                    "SELECT count(*) total, "
                            + "       count(*) FILTER (WHERE status = 'COMPLETED') completed, "
                            + "       AVG(final_rating) avg "
                            + "  FROM performance.performance_review WHERE cycle_id = :cid "
                            + scopeClause(scope, "employee_id"),
                    withScope(new MapSqlParameterSource("cid", cid), scope));

            long total = ((Number) stats.get("total")).longValue();
            long completed = ((Number) stats.get("completed")).longValue();
            BigDecimal avg = stats.get("avg") == null
                    ? null
                    : new BigDecimal(stats.get("avg").toString())
                            .setScale(2, RoundingMode.HALF_UP);

            // Distribution at half-point buckets (0, 0.5, 1.0, ... 5.0)
            List<RatingBucketRow> dist = jdbc.query(
                    "SELECT round(final_rating * 2) / 2 AS bucket, count(*) c "
                            + "  FROM performance.performance_review "
                            + " WHERE cycle_id = :cid AND final_rating IS NOT NULL "
                            + scopeClause(scope, "employee_id")
                            + " GROUP BY bucket ORDER BY bucket",
                    withScope(new MapSqlParameterSource("cid", cid), scope),
                    (rs, i) -> new RatingBucketRow(rs.getBigDecimal("bucket"), rs.getLong("c")));

            List<LabeledCount> recs = jdbc.query(
                    "SELECT COALESCE(recommendation, 'NONE') r, count(*) c "
                            + "  FROM performance.performance_review "
                            + " WHERE cycle_id = :cid "
                            + scopeClause(scope, "employee_id")
                            + " GROUP BY r ORDER BY c DESC",
                    withScope(new MapSqlParameterSource("cid", cid), scope),
                    (rs, i) -> new LabeledCount(rs.getString("r"), rs.getLong("c")));

            cycles.add(new PerformanceCycleSummary(cid, code, name, total, completed, avg, dist, recs));
        }
        return new PerformanceReport(cycles);
    }

    // ====================================================================
    //  RECRUITMENT FUNNEL
    // ====================================================================

    @Transactional(readOnly = true)
    public RecruitmentReport recruitment() {
        // ABAC: vacancies + candidate-pool have no natural employee anchor
        // (applications only become employees after HIRED). Scoped callers
        // see an empty funnel — recruitment is HR / Recruiter territory.
        if (!accessScope.isUnrestricted()) {
            return new RecruitmentReport(0L, 0L, 0L, 0L, BigDecimal.ZERO,
                    List.of(), List.of());
        }

        Long open = countNullable("SELECT count(*) FROM recruitment.vacancy WHERE status = 'OPEN'");
        Long filled = countNullable("SELECT count(*) FROM recruitment.vacancy WHERE status = 'FILLED'");
        Long candidates = countNullable("SELECT count(*) FROM recruitment.candidate");
        Long inProgressApps = countNullable(
                "SELECT count(*) FROM recruitment.application WHERE status = 'IN_PROGRESS'");

        BigDecimal avgTtH = jdbc.queryForObject(
                "SELECT AVG(EXTRACT(EPOCH FROM (e.hire_date::timestamp - cand.created_at)) / 86400) "
                        + "  FROM recruitment.application a "
                        + "  JOIN recruitment.candidate cand ON cand.id = a.candidate_id "
                        + "  JOIN core_hr.employee e ON e.id = a.created_employee_id "
                        + " WHERE a.status = 'HIRED' AND a.created_employee_id IS NOT NULL",
                new MapSqlParameterSource(), BigDecimal.class);
        if (avgTtH != null) avgTtH = avgTtH.setScale(2, RoundingMode.HALF_UP);

        List<LabeledCount> byStage = jdbc.query(
                "SELECT current_stage, count(*) c FROM recruitment.application "
                        + " WHERE status = 'IN_PROGRESS' GROUP BY current_stage ORDER BY c DESC",
                new MapSqlParameterSource(),
                (rs, i) -> new LabeledCount(rs.getString(1), rs.getLong(2)));

        List<VacancyFunnelRow> rows = jdbc.query(
                "SELECT v.id, v.vacancy_no, v.title, v.status, v.openings, "
                        + "       (SELECT count(*) FROM recruitment.application a WHERE a.vacancy_id = v.id) AS apps, "
                        + "       (SELECT count(*) FROM recruitment.application a WHERE a.vacancy_id = v.id AND a.status = 'HIRED') AS hires, "
                        + "       (SELECT AVG(EXTRACT(EPOCH FROM (e.hire_date::timestamp - cand.created_at)) / 86400) "
                        + "          FROM recruitment.application a "
                        + "          JOIN recruitment.candidate cand ON cand.id = a.candidate_id "
                        + "          JOIN core_hr.employee e ON e.id = a.created_employee_id "
                        + "         WHERE a.vacancy_id = v.id AND a.status = 'HIRED' AND a.created_employee_id IS NOT NULL) AS avg_tth "
                        + "  FROM recruitment.vacancy v "
                        + " ORDER BY v.vacancy_no",
                new MapSqlParameterSource(),
                (rs, i) -> {
                    BigDecimal av = rs.getBigDecimal("avg_tth");
                    if (av != null) av = av.setScale(2, RoundingMode.HALF_UP);
                    return new VacancyFunnelRow(
                            (UUID) rs.getObject("id"),
                            rs.getString("vacancy_no"),
                            rs.getString("title"),
                            rs.getString("status"),
                            rs.getInt("openings"),
                            rs.getLong("apps"),
                            rs.getLong("hires"),
                            av);
                });

        return new RecruitmentReport(open, filled, candidates, inProgressApps,
                avgTtH == null ? BigDecimal.ZERO : avgTtH, byStage, rows);
    }

    private long countNullable(String sql) {
        Long v = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return v == null ? 0L : v;
    }
}
