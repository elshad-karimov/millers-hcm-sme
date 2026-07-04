package az.millers.hcm.bi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data-access layer for the BI Export endpoints (PRD §17.6 / M55).
 *
 * <p>All query methods are {@code @Transactional(readOnly = true)};
 * {@link #logExport} is a separate writable transaction so it always
 * commits the audit row even if the caller's own transaction rolls back.
 *
 * <p>Queries are native SQL via {@link NamedParameterJdbcTemplate} —
 * identical pattern to {@code ReportService} — so they can join across
 * module schemas without additional JPA repositories.
 */
@Service
public class BiExportService {

    private static final Logger log = LoggerFactory.getLogger(BiExportService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final BiExportLogRepository logRepo;

    public BiExportService(NamedParameterJdbcTemplate jdbc, BiExportLogRepository logRepo) {
        this.jdbc = jdbc;
        this.logRepo = logRepo;
    }

    // =========================================================================
    //  employees
    // =========================================================================

    /**
     * Returns all employees with the fields required for BI tools.
     * Columns: id, employee_number, first_name, last_name, email,
     * department, position_title, hire_date, employment_status, cost_centre.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryEmployees() {
        return jdbc.queryForList(
                "SELECT e.id::text                      AS id, "
                        + "       e.employee_no                  AS employee_number, "
                        + "       e.first_name, "
                        + "       e.last_name, "
                        + "       e.email, "
                        + "       e.department_name              AS department, "
                        + "       e.position_title, "
                        + "       e.hire_date::text              AS hire_date, "
                        + "       e.employment_status, "
                        + "       e.cost_centre "
                        + "  FROM core_hr.employee e "
                        + " ORDER BY e.employee_no",
                new MapSqlParameterSource());
    }

    // =========================================================================
    //  payroll_runs
    // =========================================================================

    /**
     * Returns all payroll runs.
     * Columns: id, run_month (YYYY-MM), status, gross_total, tax_total,
     * net_total, employee_count, run_date.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryPayrollRuns() {
        return jdbc.queryForList(
                "SELECT pr.id::text                                           AS id, "
                        + "       to_char(make_date(pr.period_year, pr.period_month, 1), 'YYYY-MM') AS run_month, "
                        + "       pr.status, "
                        + "       pr.total_gross                             AS gross_total, "
                        + "       pr.total_income_tax                        AS tax_total, "
                        + "       pr.total_net                               AS net_total, "
                        + "       pr.employee_count, "
                        + "       pr.created_at::date::text                  AS run_date "
                        + "  FROM payroll.payroll_run pr "
                        + " ORDER BY pr.period_year DESC, pr.period_month DESC",
                new MapSqlParameterSource());
    }

    // =========================================================================
    //  leave_balances
    // =========================================================================

    /**
     * Returns leave balances per employee, leave type, and year.
     * Columns: employee_id, employee_number, leave_type_name, entitled_days,
     * used_days, remaining_days, year.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryLeaveBalances() {
        return jdbc.queryForList(
                "SELECT lb.employee_id::text                                                          AS employee_id, "
                        + "       e.employee_no                                                      AS employee_number, "
                        + "       lt.name                                                            AS leave_type_name, "
                        + "       lb.entitlement_days                                                AS entitled_days, "
                        + "       lb.used_days, "
                        + "       (lb.entitlement_days + lb.carried_forward_days + lb.adjustment_days "
                        + "        - lb.used_days - lb.reserved_days)                               AS remaining_days, "
                        + "       lb.year "
                        + "  FROM leave_mgmt.leave_balance lb "
                        + "  JOIN core_hr.employee e ON e.id = lb.employee_id "
                        + "  JOIN leave_mgmt.leave_type lt ON lt.id = lb.leave_type_id "
                        + " ORDER BY lb.year DESC, e.employee_no, lt.name",
                new MapSqlParameterSource());
    }

    // =========================================================================
    //  attendance_summary
    // =========================================================================

    /**
     * Returns monthly attendance aggregates per employee.
     * Columns: employee_id, work_date (YYYY-MM), scheduled_hours (derived from
     * schedule_start/end when available), actual_hours, overtime_hours, late_days.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryAttendanceSummary() {
        return jdbc.queryForList(
                "SELECT ds.employee_id::text                              AS employee_id, "
                        + "       to_char(date_trunc('month', ds.work_date), 'YYYY-MM') AS work_date, "
                        + "       ROUND(SUM(CASE "
                        + "           WHEN ds.schedule_start IS NOT NULL AND ds.schedule_end IS NOT NULL "
                        + "           THEN EXTRACT(EPOCH FROM (ds.schedule_end - ds.schedule_start)) / 3600.0 "
                        + "           ELSE 0 END), 2)                                   AS scheduled_hours, "
                        + "       ROUND(SUM(ds.worked_minutes)  / 60.0, 2)              AS actual_hours, "
                        + "       ROUND(SUM(ds.overtime_minutes) / 60.0, 2)             AS overtime_hours, "
                        + "       COUNT(*) FILTER (WHERE ds.late_minutes > 0)           AS late_days "
                        + "  FROM attendance.daily_summary ds "
                        + " GROUP BY ds.employee_id, date_trunc('month', ds.work_date) "
                        + " ORDER BY work_date DESC, ds.employee_id",
                new MapSqlParameterSource());
    }

    // =========================================================================
    //  headcount_trend
    // =========================================================================

    /**
     * Returns monthly headcount trend by department.
     * Columns: month (YYYY-MM), department, active_count, new_hires, terminations.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryHeadcountTrend() {
        // Active employees: snapshot by hire_date month per department.
        // New hires: employees whose hire_date falls in each month.
        // Terminations: termination_request rows processed in each month.
        // We generate a 24-month window and join the hire/termination counts.
        return jdbc.queryForList(
                "WITH months AS ( "
                        + "    SELECT to_char(generate_series( "
                        + "        date_trunc('month', current_date - interval '23 months'), "
                        + "        date_trunc('month', current_date), "
                        + "        interval '1 month'), 'YYYY-MM') AS month "
                        + "), "
                        + "depts AS ( "
                        + "    SELECT DISTINCT COALESCE(department_name, '— unassigned') AS department "
                        + "      FROM core_hr.employee "
                        + "), "
                        + "hires AS ( "
                        + "    SELECT to_char(date_trunc('month', hire_date), 'YYYY-MM') AS month, "
                        + "           COALESCE(department_name, '— unassigned')           AS department, "
                        + "           COUNT(*)                                             AS new_hires "
                        + "      FROM core_hr.employee "
                        + "     GROUP BY 1, 2 "
                        + "), "
                        + "terms AS ( "
                        + "    SELECT to_char(date_trunc('month', processed_at), 'YYYY-MM') AS month, "
                        + "           COALESCE(e.department_name, '— unassigned')            AS department, "
                        + "           COUNT(*)                                                AS terminations "
                        + "      FROM lifecycle.termination_request tr "
                        + "      JOIN core_hr.employee e ON e.id = tr.employee_id "
                        + "     WHERE tr.status = 'PROCESSED' "
                        + "     GROUP BY 1, 2 "
                        + "), "
                        + "active AS ( "
                        + "    SELECT to_char(date_trunc('month', hire_date), 'YYYY-MM')  AS month, "
                        + "           COALESCE(department_name, '— unassigned')            AS department, "
                        + "           COUNT(*) FILTER (WHERE employment_status <> 'TERMINATED') AS active_count "
                        + "      FROM core_hr.employee "
                        + "     GROUP BY 1, 2 "
                        + ") "
                        + "SELECT m.month, d.department, "
                        + "       COALESCE(a.active_count,  0) AS active_count, "
                        + "       COALESCE(h.new_hires,     0) AS new_hires, "
                        + "       COALESCE(t.terminations,  0) AS terminations "
                        + "  FROM months m "
                        + " CROSS JOIN depts d "
                        + "  LEFT JOIN hires  h ON h.month  = m.month AND h.department  = d.department "
                        + "  LEFT JOIN terms  t ON t.month  = m.month AND t.department  = d.department "
                        + "  LEFT JOIN active a ON a.month  = m.month AND a.department  = d.department "
                        + " ORDER BY m.month DESC, d.department",
                new MapSqlParameterSource());
    }

    // =========================================================================
    //  Audit logging
    // =========================================================================

    /**
     * Returns the 50 most recent export log entries, newest first.
     */
    @Transactional(readOnly = true)
    public List<BiExportLog> getRecentLog() {
        return logRepo.findTop50ByOrderByExportedAtDesc();
    }

    /**
     * Writes a {@link BiExportLog} row for the given export.
     * This method runs in its own transaction (not readOnly) so the audit
     * row is committed independently of the caller's context.
     *
     * @param entity   the entity name (e.g. "employees")
     * @param format   "ODATA" or "CSV"
     * @param rowCount number of rows exported
     */
    @Transactional
    public void logExport(String entity, String format, int rowCount) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String requestedBy = auth != null ? auth.getName() : "anonymous";

        BiExportLog entry = new BiExportLog();
        entry.setEntity(entity);
        entry.setFormat(format);
        entry.setRequestedBy(requestedBy);
        entry.setRowCount(rowCount);
        entry.setExportedAt(OffsetDateTime.now());
        logRepo.save(entry);

        log.info("BI export: entity={} format={} rows={} by={}", entity, format, rowCount, requestedBy);
    }
}
