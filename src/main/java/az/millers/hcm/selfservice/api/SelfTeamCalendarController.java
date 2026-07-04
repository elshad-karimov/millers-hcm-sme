package az.millers.hcm.selfservice.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.api.dto.TeamCalendarDtos.TeamCalendarResponse;
import az.millers.hcm.leave.service.TeamCalendarService;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M431 — Team calendar in ESS: GET /api/self/team-calendar → if current
 * employee has direct reports, delegate to TeamCalendarService for their team.
 */
@RestController
@RequestMapping("/api/self")
public class SelfTeamCalendarController {

    private final EmployeeContextService employeeContext;
    private final EmployeeRepository employees;
    private final TeamCalendarService teamCalendarService;

    public SelfTeamCalendarController(EmployeeContextService employeeContext,
                                       EmployeeRepository employees,
                                       TeamCalendarService teamCalendarService) {
        this.employeeContext = employeeContext;
        this.employees = employees;
        this.teamCalendarService = teamCalendarService;
    }

    /**
     * Returns team calendar if the current employee has direct reports (is a manager),
     * else returns {manager: false}.
     */
    @GetMapping("/team-calendar")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'HR_ADMIN', 'HR_SPECIALIST')")
    public Object teamCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate windowStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate windowEnd,
            @RequestParam(required = false) BigDecimal thresholdPercent) {

        Employee me = employeeContext.currentEmployee();

        // Check if the current employee has direct reports
        boolean hasReports = !employees.findDirectReports(me.getId()).isEmpty();
        if (!hasReports) {
            Map<String, Object> response = new HashMap<>();
            response.put("manager", false);
            return response;
        }

        // Default window if not specified: current month
        LocalDate start = windowStart != null ? windowStart : LocalDate.now().withDayOfMonth(1);
        LocalDate end = windowEnd != null ? windowEnd : start.withDayOfMonth(start.lengthOfMonth());

        // Delegate to the existing TeamCalendarService
        // orgUnitId = null means caller's scope (manager → reports)
        TeamCalendarResponse calendar = teamCalendarService.teamCalendar(
                null, // orgUnitId = null → manager's direct reports
                start,
                end,
                thresholdPercent
        );

        Map<String, Object> response = new HashMap<>();
        response.put("manager", true);
        response.put("calendar", calendar);
        return response;
    }
}
