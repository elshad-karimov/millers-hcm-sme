package az.millers.hcm.analytics;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Executes analytical queries against the ClickHouse warehouse.
 */
@Service
public class WarehouseAnalyticsService {

    private final ClickHouseClient ch;

    public WarehouseAnalyticsService(ClickHouseClient ch) {
        this.ch = ch;
    }

    /** Headcount by department (current snapshot). */
    public List<Map<String, Object>> headcountByDept() {
        return ch.query("""
            SELECT
                department_name,
                countDistinct(employee_id) AS headcount
            FROM fact_employee
            WHERE status IN ('ACTIVE', 'On Probation')
            GROUP BY department_name
            ORDER BY headcount DESC
            """);
    }

    /** Daily attendance event count for the last 60 days. */
    public List<Map<String, Object>> attendanceTrend() {
        return ch.query("""
            SELECT
                toString(event_date) AS event_date,
                countIf(event_type = 'IN')  AS check_ins,
                countIf(event_type = 'OUT') AS check_outs
            FROM fact_attendance
            WHERE event_date >= today() - 60
            GROUP BY event_date
            ORDER BY event_date
            """);
    }

    /** Monthly gross and net payroll for the last 12 months. */
    public List<Map<String, Object>> payrollTrend() {
        return ch.query("""
            SELECT
                period_year,
                period_month,
                round(sum(gross_amount), 2) AS total_gross,
                round(sum(net_amount),   2) AS total_net,
                count()                      AS employee_count
            FROM fact_payroll_result
            GROUP BY period_year, period_month
            ORDER BY period_year, period_month
            LIMIT 12
            """);
    }

    /** Leave days requested/approved by type (last 12 months). */
    public List<Map<String, Object>> leaveSummary() {
        return ch.query("""
            SELECT
                leave_type_name,
                round(sum(total_days), 1)              AS days_total,
                countIf(status = 'APPROVED')           AS approved_count,
                countIf(status = 'PENDING')            AS pending_count
            FROM fact_leave_request
            WHERE start_date >= toDate(now()) - 365
            GROUP BY leave_type_name
            ORDER BY days_total DESC
            """);
    }

    public boolean isAvailable() {
        return ch.ping();
    }
}
