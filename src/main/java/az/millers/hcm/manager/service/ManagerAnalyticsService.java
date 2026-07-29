package az.millers.hcm.manager.service;
import az.millers.hcm.common.tenant.TenantContext;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * M434 — Manager analytics: team headcount, turnover, absence, overtime,
 * training completion, open skill gaps (all AccessScope-scoped to own reports).
 */
@Service
public class ManagerAnalyticsService {
    private final EmployeeContextService context;
    private final EmployeeRepository employees;
    private final NamedParameterJdbcTemplate jdbc;

    public ManagerAnalyticsService(EmployeeContextService context, EmployeeRepository employees,
                                    NamedParameterJdbcTemplate jdbc) {
        this.context = context;
        this.employees = employees;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics() {
        Employee me = context.currentEmployee();
        String tenantId = TenantContext.current(); // tenant_id constant (Employee JPA entity doesn't map it)
        List<Employee> reports = employees.findDirectReports(me.getId());
        List<UUID> reportIds = reports.stream().map(Employee::getId).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("teamHeadcount", reports.size());

        if (reportIds.isEmpty()) {
            result.put("turnoverRate12m", 0.0);
            result.put("absenceRateCurrentMonth", 0.0);
            result.put("overtimeHoursLast3Months", 0.0);
            result.put("trainingCompletion", 0.0);
            result.put("openSkillGaps", 0);
            return result;
        }

        // Turnover: exits in last 12 months / max(1, headcount)
        String turnoverSql = """
            SELECT COUNT(DISTINCT employee_id)
            FROM lifecycle.termination_request
            WHERE tenant_id = :tenantId
              AND employee_id = ANY(:reportIds)
              AND status = 'APPROVED'
              AND effective_date >= CURRENT_DATE - INTERVAL '12 months'
            """;
        Long exits = jdbc.queryForObject(turnoverSql,
                Map.of("tenantId", tenantId, "reportIds", reportIds.toArray(new UUID[0])),
                Long.class);
        double turnover = (exits == null ? 0 : exits.doubleValue()) / Math.max(1, reportIds.size());
        result.put("turnoverRate12m", turnover);

        // Absence rate: approved leave days this month / (reports × 22 working days)
        String absenceSql = """
            SELECT COALESCE(SUM(total_days), 0)
            FROM leave_mgmt.leave_request
            WHERE employee_id = ANY(:reportIds)
              AND status = 'APPROVED'
              AND start_date <= (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date
              AND end_date >= DATE_TRUNC('month', CURRENT_DATE)::date
            """;
        Double leaveDays = jdbc.queryForObject(absenceSql,
                Map.of("reportIds", reportIds.toArray(new UUID[0])),
                Double.class);
        double absenceRate = (leaveDays == null ? 0 : leaveDays) / Math.max(1, reportIds.size() * 22);
        result.put("absenceRateCurrentMonth", absenceRate);

        // Overtime hours: sum approved OT hours in last 3 months
        String overtimeSql = """
            SELECT COALESCE(SUM(approved_hours), 0)
            FROM attendance.overtime_request
            WHERE tenant_id = :tenantId
              AND employee_id = ANY(:reportIds)
              AND workflow_status = 'APPROVED'
              AND work_date >= CURRENT_DATE - INTERVAL '3 months'
            """;
        Double otHours = jdbc.queryForObject(overtimeSql,
                Map.of("tenantId", tenantId, "reportIds", reportIds.toArray(new UUID[0])),
                Double.class);
        result.put("overtimeHoursLast3Months", otHours == null ? 0.0 : otHours);

        // Training completion: PASSED enrollments / total enrollments
        String trainingSql = """
            SELECT
                COUNT(*) FILTER (WHERE status = 'PASSED') AS passed,
                COUNT(*) AS total
            FROM learning.enrollment
            WHERE employee_id = ANY(:reportIds)
            """;
        Map<String, Object> trainingStats = jdbc.queryForMap(trainingSql,
                Map.of("reportIds", reportIds.toArray(new UUID[0])));
        long totalEnroll = ((Number) trainingStats.get("total")).longValue();
        long passedEnroll = ((Number) trainingStats.get("passed")).longValue();
        double completion = totalEnroll > 0 ? (double) passedEnroll / totalEnroll : 0.0;
        result.put("trainingCompletion", completion);

        // Open skill gaps: count of position-required competencies unmet
        // Use position competency requirements that employees don't have at the required level
        String gapsSql = """
            SELECT COUNT(*)
            FROM staffing.position_competency pc
            JOIN core_hr.employee e ON e.position_id = pc.position_id
            WHERE e.id = ANY(:reportIds)
              AND pc.tenant_id = :tenantId
              AND NOT EXISTS (
                  SELECT 1
                  FROM learning.employee_competency ec
                  WHERE ec.employee_id = e.id
                    AND ec.competency_id = pc.competency_id
                    AND ec.proficiency_level >= pc.required_level
              )
            """;
        Long gaps = jdbc.queryForObject(gapsSql,
                Map.of("tenantId", tenantId, "reportIds", reportIds.toArray(new UUID[0])),
                Long.class);
        result.put("openSkillGaps", gaps == null ? 0 : gaps.intValue());

        return result;
    }
}
