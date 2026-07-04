package az.millers.hcm.manager.service;

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
        List<Employee> reports = employees.findDirectReports(me.getId());
        List<UUID> reportIds = reports.stream().map(Employee::getId).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("teamHeadcount", reports.size());
        result.put("turnoverRate12m", 0.0); // Placeholder: compute exits/avg headcount
        result.put("absenceRateCurrentMonth", 0.0); // Placeholder: leave days / working days
        result.put("overtimeHoursLast3Months", 0.0); // Placeholder: sum overtime
        result.put("trainingCompletion", 0.0); // Placeholder: PASSED / total enrollments
        result.put("openSkillGaps", 0); // Placeholder: count skill gaps

        return result;
    }
}
