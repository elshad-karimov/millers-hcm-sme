package az.millers.hcm.reporting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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

import az.millers.hcm.reporting.api.dto.DashboardDtos.ExecutiveDashboard;
import az.millers.hcm.reporting.api.dto.DashboardDtos.HeadcountTrend;
import az.millers.hcm.reporting.api.dto.DashboardDtos.LabeledValue;
import az.millers.hcm.reporting.api.dto.DashboardDtos.ManagerTeamDashboard;
import az.millers.hcm.reporting.api.dto.DashboardDtos.MonthPoint;
import az.millers.hcm.reporting.api.dto.DashboardDtos.PayrollMonthPoint;
import az.millers.hcm.reporting.api.dto.DashboardDtos.PayrollTrend;
import az.millers.hcm.reporting.api.dto.DashboardDtos.TeamMemberRow;
import az.millers.hcm.reporting.api.dto.DashboardDtos.TurnoverDashboard;
import az.millers.hcm.reporting.api.dto.DashboardDtos.UpcomingLeaveRow;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Aggregation service backing the analytics dashboard endpoints (PRD 10.3).
 *
 * <p>Uses {@link NamedParameterJdbcTemplate} for cross-schema native SQL,
 * following the same conventions as {@link ReportService}.
 */
@Service
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AccessScopeService accessScope;

    public DashboardService(NamedParameterJdbcTemplate jdbc, AccessScopeService accessScope) {
        this.jdbc = jdbc;
        this.accessScope = accessScope;
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    private static String scopeClause(Set<UUID> scope, String col) {
        return scope == null ? "" : " AND " + col + " IN (:scopeIds) ";
    }

    private static MapSqlParameterSource withScope(MapSqlParameterSource p, Set<UUID> scope) {
        if (scope != null) p.addValue("scopeIds", scope);
        return p;
    }

    // ------------------------------------------------------------------ //
    //  Headcount trend                                                    //
    // ------------------------------------------------------------------ //

    /**
     * Returns monthly active-employee count, hires, and leavers for the
     * past {@code months} months, plus department and status breakdowns.
     */
    @Transactional(readOnly = true)
    public HeadcountTrend headcountTrend(int months) {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope != null && scope.isEmpty()) {
            return new HeadcountTrend(List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // Build monthly active-headcount series by counting employees whose
        // hire_date <= last day of the month and (no processed termination with
        // effective_date in that month or earlier).
        // For simplicity (and correctness on small datasets) we group by
        // truncated hire_date for hires and by truncated effective_date for
        // leavers, then fill the headcount from the live running total.

        List<MonthPoint> hires = jdbc.query(
                "SELECT to_char(date_trunc('month', hire_date), 'YYYY-MM') AS m, count(*) c"
                        + " FROM core_hr.employee"
                        + " WHERE hire_date >= (current_date - (:months || ' months')::interval)"
                        + scopeClause(scope, "id")
                        + " GROUP BY m ORDER BY m",
                withScope(new MapSqlParameterSource("months", months), scope),
                (rs, i) -> new MonthPoint(rs.getString(1), rs.getLong(2)));

        List<MonthPoint> leavers = jdbc.query(
                "SELECT to_char(date_trunc('month', effective_date), 'YYYY-MM') AS m, count(*) c"
                        + " FROM lifecycle.termination_request"
                        + " WHERE status = 'PROCESSED'"
                        + "   AND effective_date >= (current_date - (:months || ' months')::interval)"
                        + scopeClause(scope, "employee_id")
                        + " GROUP BY m ORDER BY m",
                withScope(new MapSqlParameterSource("months", months), scope),
                (rs, i) -> new MonthPoint(rs.getString(1), rs.getLong(2)));

        // Headcount per month: count employees hired on or before month-end
        // minus those terminated on or before month-end
        List<MonthPoint> headcount = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            LocalDate monthEnd = ym.atEndOfMonth();
            String label = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            Long active = jdbc.queryForObject(
                    "SELECT count(*) FROM core_hr.employee e"
                            + " WHERE e.hire_date <= :end"
                            + " AND NOT EXISTS ("
                            + "   SELECT 1 FROM lifecycle.termination_request t"
                            + "   WHERE t.employee_id = e.id"
                            + "     AND t.status = 'PROCESSED'"
                            + "     AND t.effective_date <= :end"
                            + " )"
                            + scopeClause(scope, "e.id"),
                    withScope(new MapSqlParameterSource("end", monthEnd), scope),
                    Long.class);
            headcount.add(new MonthPoint(label, active == null ? 0 : active));
        }

        List<LabeledValue> byDept = jdbc.query(
                "SELECT COALESCE(department_name,'— unassigned') AS d, count(*) c"
                        + " FROM core_hr.employee"
                        + " WHERE employment_status NOT IN ('TERMINATED','RETIRED')"
                        + scopeClause(scope, "id")
                        + " GROUP BY d ORDER BY c DESC",
                withScope(new MapSqlParameterSource(), scope),
                (rs, i) -> new LabeledValue(rs.getString(1), rs.getLong(2)));

        List<LabeledValue> byStatus = jdbc.query(
                "SELECT employment_status, count(*) c"
                        + " FROM core_hr.employee"
                        + " WHERE 1=1" + scopeClause(scope, "id")
                        + " GROUP BY employment_status ORDER BY c DESC",
                withScope(new MapSqlParameterSource(), scope),
                (rs, i) -> new LabeledValue(rs.getString(1), rs.getLong(2)));

        return new HeadcountTrend(headcount, hires, leavers, byDept, byStatus);
    }

    // ------------------------------------------------------------------ //
    //  Payroll trend                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Returns monthly gross / net / tax totals from closed payroll runs for
     * the requested year.
     */
    @Transactional(readOnly = true)
    public PayrollTrend payrollTrend(int year) {
        List<PayrollMonthPoint> monthly = jdbc.query(
                "SELECT period_month, total_gross, total_net, total_income_tax, employee_count"
                        + " FROM payroll.payroll_run"
                        + " WHERE period_year = :year"
                        + "   AND status IN ('APPROVED','PAID','CLOSED')"
                        + " ORDER BY period_month",
                new MapSqlParameterSource("year", year),
                (rs, i) -> new PayrollMonthPoint(
                        year + "-" + String.format("%02d", rs.getInt(1)),
                        rs.getBigDecimal(2) == null ? 0 : rs.getBigDecimal(2).doubleValue(),
                        rs.getBigDecimal(3) == null ? 0 : rs.getBigDecimal(3).doubleValue(),
                        rs.getBigDecimal(4) == null ? 0 : rs.getBigDecimal(4).doubleValue(),
                        rs.getInt(5)));

        double totalGross = monthly.stream().mapToDouble(PayrollMonthPoint::gross).sum();
        double totalNet   = monthly.stream().mapToDouble(PayrollMonthPoint::net).sum();
        double totalTax   = monthly.stream().mapToDouble(PayrollMonthPoint::tax).sum();

        return new PayrollTrend(monthly, totalGross, totalNet, totalTax);
    }

    // ------------------------------------------------------------------ //
    //  Turnover dashboard                                                 //
    // ------------------------------------------------------------------ //

    /**
     * Monthly attrition rates, termination-reason breakdown, and average
     * employee tenure at termination for the given year.
     */
    @Transactional(readOnly = true)
    public TurnoverDashboard turnover(int year) {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope != null && scope.isEmpty()) {
            return new TurnoverDashboard(year, 0, BigDecimal.ZERO, 0, List.of(), List.of());
        }

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM lifecycle.termination_request"
                        + " WHERE status = 'PROCESSED'"
                        + "   AND date_part('year', effective_date) = :year"
                        + scopeClause(scope, "employee_id"),
                withScope(new MapSqlParameterSource("year", year), scope),
                Long.class);
        if (total == null) total = 0L;

        // Average tenure in months (hire → effective termination date)
        // PostgreSQL date subtraction returns integer days, so divide by 30
        Double avgTenure = jdbc.queryForObject(
                "SELECT AVG((t.effective_date - e.hire_date) / 30.0)"
                        + " FROM lifecycle.termination_request t"
                        + " JOIN core_hr.employee e ON e.id = t.employee_id"
                        + " WHERE t.status = 'PROCESSED'"
                        + "   AND date_part('year', t.effective_date) = :year"
                        + scopeClause(scope, "t.employee_id"),
                withScope(new MapSqlParameterSource("year", year), scope),
                Double.class);
        double avg = avgTenure == null ? 0.0 : Math.round(avgTenure * 10.0) / 10.0;

        // Active headcount for attrition denominator
        Long activeCount = jdbc.queryForObject(
                "SELECT count(*) FROM core_hr.employee"
                        + " WHERE employment_status NOT IN ('TERMINATED','RETIRED')"
                        + scopeClause(scope, "id"),
                withScope(new MapSqlParameterSource(), scope),
                Long.class);
        long denom = Math.max(1L, (activeCount == null ? 0 : activeCount) + total);
        BigDecimal attritionRate = BigDecimal.valueOf(total)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denom), 2, RoundingMode.HALF_UP);

        // Monthly attrition rate (percentage of headcount at time)
        List<MonthPoint> monthly = jdbc.query(
                "SELECT to_char(date_trunc('month', effective_date), 'YYYY-MM') AS m,"
                        + "       count(*) c"
                        + " FROM lifecycle.termination_request"
                        + " WHERE status = 'PROCESSED'"
                        + "   AND date_part('year', effective_date) = :year"
                        + scopeClause(scope, "employee_id")
                        + " GROUP BY m ORDER BY m",
                withScope(new MapSqlParameterSource("year", year), scope),
                (rs, i) -> new MonthPoint(rs.getString(1), rs.getLong(2)));

        List<LabeledValue> byReason = jdbc.query(
                "SELECT reason_code, count(*) c"
                        + " FROM lifecycle.termination_request"
                        + " WHERE status = 'PROCESSED'"
                        + "   AND date_part('year', effective_date) = :year"
                        + scopeClause(scope, "employee_id")
                        + " GROUP BY reason_code ORDER BY c DESC",
                withScope(new MapSqlParameterSource("year", year), scope),
                (rs, i) -> new LabeledValue(rs.getString(1), rs.getLong(2)));

        return new TurnoverDashboard(year, total.intValue(), attritionRate, avg, monthly, byReason);
    }

    // ------------------------------------------------------------------ //
    //  Manager team dashboard                                             //
    // ------------------------------------------------------------------ //

    /**
     * Returns a team overview for the currently authenticated
     * DEPARTMENT_MANAGER: member list, status breakdown, pending approvals,
     * and leave requests starting in the next 30 days.
     */
    @Transactional(readOnly = true)
    public ManagerTeamDashboard managerTeam(UUID managerId) {
        // Direct reports only (not full subtree — keeps the query cheap)
        List<TeamMemberRow> members = jdbc.query(
                "SELECT employee_no, first_name, last_name,"
                        + "       COALESCE(position_title,'—') AS pos, employment_status"
                        + " FROM core_hr.employee"
                        + " WHERE manager_id = :mgr"
                        + "   AND employment_status NOT IN ('TERMINATED','RETIRED')"
                        + " ORDER BY last_name, first_name",
                new MapSqlParameterSource("mgr", managerId),
                (rs, i) -> new TeamMemberRow(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)));

        int teamSize = members.size();

        Map<String, Long> statusMap = new HashMap<>();
        members.forEach(m -> statusMap.merge(m.employmentStatus(), 1L, Long::sum));
        List<LabeledValue> statusBreakdown = statusMap.entrySet().stream()
                .map(e -> new LabeledValue(e.getKey(), e.getValue()))
                .sorted((a, b) -> Double.compare(b.value(), a.value()))
                .toList();

        // Pending approvals for the manager's role in the workflow inbox
        // (workflow_instance.current_step_role is the role that must act next)
        Long pending = jdbc.queryForObject(
                "SELECT count(*) FROM workflow.workflow_instance"
                        + " WHERE status = 'PENDING'"
                        + "   AND current_step_role = 'DEPARTMENT_MANAGER'",
                new MapSqlParameterSource(),
                Long.class);

        // Upcoming leave in team (next 30 days)
        List<UpcomingLeaveRow> upcoming = jdbc.query(
                "SELECT e.first_name || ' ' || e.last_name AS name,"
                        + "       lt.code, lr.start_date, lr.end_date, lr.total_days"
                        + " FROM leave_mgmt.leave_request lr"
                        + " JOIN core_hr.employee e ON e.id = lr.employee_id"
                        + " JOIN leave_mgmt.leave_type lt ON lt.id = lr.leave_type_id"
                        + " WHERE e.manager_id = :mgr"
                        + "   AND lr.status IN ('PENDING','APPROVED')"
                        + "   AND lr.start_date BETWEEN current_date AND (current_date + interval '30 days')"
                        + " ORDER BY lr.start_date",
                new MapSqlParameterSource("mgr", managerId),
                (rs, i) -> new UpcomingLeaveRow(
                        rs.getString(1), rs.getString(2),
                        rs.getDate(3).toString(), rs.getDate(4).toString(),
                        rs.getDouble(5)));

        return new ManagerTeamDashboard(
                teamSize, pending == null ? 0 : pending.intValue(),
                statusBreakdown, members, upcoming);
    }

    // ------------------------------------------------------------------ //
    //  Executive dashboard                                                //
    // ------------------------------------------------------------------ //

    /**
     * Cross-module KPI snapshot for the executive view.
     */
    @Transactional(readOnly = true)
    public ExecutiveDashboard executive() {
        int year  = Year.now().getValue();

        Long headcount = jdbc.queryForObject(
                "SELECT count(*) FROM core_hr.employee"
                        + " WHERE employment_status NOT IN ('TERMINATED','RETIRED')",
                new MapSqlParameterSource(), Long.class);

        // Current month payroll cost (most recent closed run)
        Double payroll = jdbc.queryForObject(
                "SELECT COALESCE(total_gross, 0) FROM payroll.payroll_run"
                        + " WHERE period_year = :y AND status IN ('APPROVED','PAID','CLOSED')"
                        + " ORDER BY period_month DESC LIMIT 1",
                new MapSqlParameterSource("y", year), Double.class);

        Long termYtd = jdbc.queryForObject(
                "SELECT count(*) FROM lifecycle.termination_request"
                        + " WHERE status = 'PROCESSED' AND date_part('year', effective_date) = :y",
                new MapSqlParameterSource("y", year), Long.class);
        if (termYtd == null) termYtd = 0L;
        long hc = headcount == null ? 1 : headcount;
        BigDecimal attrition = BigDecimal.valueOf(termYtd)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(Math.max(1, hc + termYtd)), 2, RoundingMode.HALF_UP);

        // Training compliance: enrolled-and-completed / total active employees
        Long compliant = jdbc.queryForObject(
                "SELECT count(DISTINCT en.employee_id)"
                        + " FROM learning.enrollment en"
                        + " JOIN learning.course c ON c.id = en.course_id"
                        + " WHERE c.mandatory = true AND en.status = 'COMPLETED'",
                new MapSqlParameterSource(), Long.class);
        Long mandatory = jdbc.queryForObject(
                "SELECT count(*) FROM learning.course WHERE mandatory = true",
                new MapSqlParameterSource(), Long.class);
        double compliance = (mandatory == null || mandatory == 0) ? 100.0
                : Math.min(100.0, (compliant == null ? 0 : compliant) * 100.0 / Math.max(1, hc));

        Long openVacancies = jdbc.queryForObject(
                "SELECT count(*) FROM recruitment.vacancy WHERE status = 'OPEN'",
                new MapSqlParameterSource(), Long.class);

        Long pendingApprovals = jdbc.queryForObject(
                "SELECT count(*) FROM workflow.workflow_instance WHERE status = 'PENDING'",
                new MapSqlParameterSource(), Long.class);

        // Reuse headcount-trend and payroll-trend (last 6 months for exec view)
        Set<UUID> noScope = null;
        List<MonthPoint> hcTrend = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            LocalDate monthEnd = ym.atEndOfMonth();
            String label = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            Long active = jdbc.queryForObject(
                    "SELECT count(*) FROM core_hr.employee e"
                            + " WHERE e.hire_date <= :end"
                            + " AND NOT EXISTS ("
                            + "   SELECT 1 FROM lifecycle.termination_request t"
                            + "   WHERE t.employee_id = e.id"
                            + "     AND t.status = 'PROCESSED'"
                            + "     AND t.effective_date <= :end"
                            + " )",
                    new MapSqlParameterSource("end", monthEnd),
                    Long.class);
            hcTrend.add(new MonthPoint(label, active == null ? 0 : active));
        }

        List<PayrollMonthPoint> prTrend = jdbc.query(
                "SELECT period_month, total_gross, total_net, total_income_tax, employee_count"
                        + " FROM payroll.payroll_run"
                        + " WHERE period_year = :year"
                        + "   AND status IN ('APPROVED','PAID','CLOSED')"
                        + " ORDER BY period_month",
                new MapSqlParameterSource("year", year),
                (rs, i) -> new PayrollMonthPoint(
                        year + "-" + String.format("%02d", rs.getInt(1)),
                        rs.getBigDecimal(2) == null ? 0 : rs.getBigDecimal(2).doubleValue(),
                        rs.getBigDecimal(3) == null ? 0 : rs.getBigDecimal(3).doubleValue(),
                        rs.getBigDecimal(4) == null ? 0 : rs.getBigDecimal(4).doubleValue(),
                        rs.getInt(5)));

        return new ExecutiveDashboard(
                (int) hc,
                payroll == null ? 0 : payroll,
                attrition,
                Math.round(compliance * 10.0) / 10.0,
                openVacancies == null ? 0 : openVacancies.intValue(),
                pendingApprovals == null ? 0 : pendingApprovals.intValue(),
                hcTrend,
                prTrend);
    }
}
