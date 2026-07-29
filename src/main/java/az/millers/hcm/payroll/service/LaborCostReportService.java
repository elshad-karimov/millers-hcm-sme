package az.millers.hcm.payroll.service;
import az.millers.hcm.common.tenant.TenantContext;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M486: Labor cost reports (Finance/HR confidential).
 */
@Service
public class LaborCostReportService {


    private final NamedParameterJdbcTemplate jdbc;

    public LaborCostReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Labor cost by project for given period.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> byProject(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        String sql = """
            SELECT
                p.code AS project_code,
                p.name AS project_name,
                SUM(lca.hours) AS total_hours,
                SUM(lca.amount) AS total_cost,
                COUNT(DISTINCT lca.employee_id) AS employee_count
            FROM payroll.labor_cost_allocation lca
            JOIN timesheet.project p ON lca.project_id = p.id
            WHERE lca.tenant_id = :tenantId
              AND lca.work_date BETWEEN :from AND :to
              AND lca.project_id IS NOT NULL
            GROUP BY p.id, p.code, p.name
            ORDER BY total_cost DESC
            """;

        return jdbc.queryForList(sql,
            new MapSqlParameterSource()
                .addValue("tenantId", TenantContext.current())
                .addValue("from", from)
                .addValue("to", to));
    }

    /**
     * Labor cost by department/org unit for given period.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> byDepartment(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        String sql = """
            SELECT
                ou.code AS dept_code,
                ou.name AS dept_name,
                SUM(lca.hours) AS total_hours,
                SUM(lca.amount) AS total_cost,
                COUNT(DISTINCT lca.employee_id) AS employee_count
            FROM payroll.labor_cost_allocation lca
            JOIN core_hr.employee e ON lca.employee_id = e.id
            JOIN organization.org_unit ou ON e.scope_org_unit_id = ou.id
            WHERE lca.tenant_id = :tenantId
              AND lca.work_date BETWEEN :from AND :to
            GROUP BY ou.id, ou.code, ou.name
            ORDER BY total_cost DESC
            """;

        return jdbc.queryForList(sql,
            new MapSqlParameterSource()
                .addValue("tenantId", TenantContext.current())
                .addValue("from", from)
                .addValue("to", to));
    }

    /**
     * Labor cost summary for given period (grouped by work_date).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> monthlySummary(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        String sql = """
            SELECT
                work_date,
                SUM(hours) AS total_hours,
                SUM(amount) AS total_cost,
                COUNT(DISTINCT employee_id) AS employee_count
            FROM payroll.labor_cost_allocation
            WHERE tenant_id = :tenantId
              AND work_date BETWEEN :from AND :to
            GROUP BY work_date
            ORDER BY work_date
            """;

        return jdbc.queryForList(sql,
            new MapSqlParameterSource()
                .addValue("tenantId", TenantContext.current())
                .addValue("from", from)
                .addValue("to", to));
    }
}
