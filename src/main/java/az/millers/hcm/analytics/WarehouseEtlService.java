package az.millers.hcm.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ETL service: pulls data from PostgreSQL and loads into ClickHouse.
 * Scheduled nightly; also triggerable via REST for admin use.
 */
@Service
public class WarehouseEtlService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseEtlService.class);

    private final ClickHouseClient ch;
    private final NamedParameterJdbcTemplate jdbc;

    public WarehouseEtlService(ClickHouseClient ch, NamedParameterJdbcTemplate jdbc) {
        this.ch = ch;
        this.jdbc = jdbc;
    }

    /** Nightly sync at 02:00. */
    @Scheduled(cron = "0 0 2 * * ?")
    public SyncResult scheduledSync() {
        log.info("Starting scheduled warehouse ETL sync");
        return sync();
    }

    public SyncResult sync() {
        if (!ch.ping()) {
            return new SyncResult(false, "ClickHouse not reachable", 0, 0, 0, 0);
        }
        int employees = 0, attendance = 0, payroll = 0, leave = 0;
        try {
            employees = syncEmployees();
            attendance = syncAttendance();
            payroll = syncPayroll();
            leave = syncLeave();
            log.info("Warehouse sync complete: emp={} att={} pay={} leave={}", employees, attendance, payroll, leave);
            return new SyncResult(true, "OK", employees, attendance, payroll, leave);
        } catch (Exception e) {
            log.error("Warehouse sync failed", e);
            return new SyncResult(false, e.getMessage(), employees, attendance, payroll, leave);
        }
    }

    private int syncEmployees() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            """
            SELECT
                id::text             AS employee_id,
                CURRENT_DATE::text   AS snapshot_date,
                first_name,
                last_name,
                COALESCE(department_name, '') AS department_name,
                COALESCE(employment_status, 'ACTIVE') AS status,
                COALESCE(hire_date::text, '') AS hire_date
            FROM core_hr.employee
            """, Collections.emptyMap());
        // Truncate and reload (snapshot pattern)
        ch.execute("TRUNCATE TABLE fact_employee");
        List<Map<String, Object>> mapped = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employee_id",    str(r, "employee_id"));
            m.put("snapshot_date",  str(r, "snapshot_date"));
            m.put("first_name",     str(r, "first_name"));
            m.put("last_name",      str(r, "last_name"));
            m.put("department_name",str(r, "department_name"));
            m.put("status",         str(r, "status"));
            m.put("hire_date",      str(r, "hire_date"));
            return m;
        }).toList();
        ch.insertBatch("fact_employee", mapped);
        return mapped.size();
    }

    private int syncAttendance() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            """
            SELECT
                id::text                 AS event_id,
                employee_id::text,
                event_time::date::text   AS event_date,
                event_type,
                ''                       AS department_name
            FROM attendance.attendance_event
            """, Collections.emptyMap());
        ch.execute("TRUNCATE TABLE fact_attendance");
        List<Map<String, Object>> mapped = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("event_id",       str(r, "event_id"));
            m.put("employee_id",    str(r, "employee_id"));
            m.put("event_date",     str(r, "event_date"));
            m.put("event_type",     str(r, "event_type"));
            m.put("department_name",str(r, "department_name"));
            return m;
        }).toList();
        ch.insertBatch("fact_attendance", mapped);
        return mapped.size();
    }

    private int syncPayroll() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            """
            SELECT
                r.id::text        AS result_id,
                r.run_id::text,
                r.employee_id::text,
                run.period_year,
                run.period_month,
                r.gross_amount,
                r.net_amount,
                r.income_tax,
                r.dsmf_employee
            FROM payroll.payroll_result r
            JOIN payroll.payroll_run run ON run.id = r.run_id
            WHERE run.status IN ('APPROVED','PAID','CLOSED')
            """, Collections.emptyMap());
        ch.execute("TRUNCATE TABLE fact_payroll_result");
        List<Map<String, Object>> mapped = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("result_id",     str(r, "result_id"));
            m.put("run_id",        str(r, "run_id"));
            m.put("employee_id",   str(r, "employee_id"));
            m.put("period_year",   r.get("period_year"));
            m.put("period_month",  r.get("period_month"));
            m.put("gross_amount",  r.get("gross_amount"));
            m.put("net_amount",    r.get("net_amount"));
            m.put("income_tax",    r.get("income_tax"));
            m.put("dsmf_employee", r.get("dsmf_employee"));
            return m;
        }).toList();
        ch.insertBatch("fact_payroll_result", mapped);
        return mapped.size();
    }

    private int syncLeave() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            """
            SELECT
                lr.id::text              AS request_id,
                lr.employee_id::text,
                lt.name                  AS leave_type_name,
                lr.start_date::text,
                lr.end_date::text,
                lr.total_days,
                lr.status,
                COALESCE(e.department_name, '') AS department_name
            FROM leave_mgmt.leave_request lr
            JOIN leave_mgmt.leave_type lt ON lt.id = lr.leave_type_id
            JOIN core_hr.employee e ON e.id = lr.employee_id
            """, Collections.emptyMap());
        ch.execute("TRUNCATE TABLE fact_leave_request");
        List<Map<String, Object>> mapped = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("request_id",      str(r, "request_id"));
            m.put("employee_id",     str(r, "employee_id"));
            m.put("leave_type_name", str(r, "leave_type_name"));
            m.put("start_date",      str(r, "start_date"));
            m.put("end_date",        str(r, "end_date"));
            m.put("total_days",      r.get("total_days"));
            m.put("status",          str(r, "status"));
            m.put("department_name", str(r, "department_name"));
            return m;
        }).toList();
        ch.insertBatch("fact_leave_request", mapped);
        return mapped.size();
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : v.toString();
    }
}
