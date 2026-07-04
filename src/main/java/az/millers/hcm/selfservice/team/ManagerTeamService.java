package az.millers.hcm.selfservice.team;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.lifecycle.domain.EmploymentContract;
import az.millers.hcm.lifecycle.domain.ProbationReview;
import az.millers.hcm.lifecycle.repo.EmploymentContractRepository;
import az.millers.hcm.lifecycle.repo.ProbationReviewRepository;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import az.millers.hcm.selfservice.team.TeamDtos.TeamMember;
import az.millers.hcm.selfservice.team.TeamDtos.TeamSummary;

/**
 * Manager self-service team dashboard service (M76 / P2-11/12).
 *
 * <p>Re-uses the M27 / M30 manager-chain plumbing — for the dashboard we want
 * the immediate direct reports (the team you actually line-manage). For
 * transitive scope (everyone who reports up through you) the existing
 * {@code AccessScopeService} stays the canonical source; we don't duplicate
 * that here.
 */
@Service
public class ManagerTeamService {

    private static final int LOOKAHEAD_DAYS = 60;

    private final EmployeeContextService context;
    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;
    private final EmploymentContractRepository contracts;
    private final ProbationReviewRepository probationReviews;
    private final SettingService settings;
    private final NamedParameterJdbcTemplate jdbc;

    public ManagerTeamService(EmployeeContextService context,
                               EmployeeRepository employees,
                               LeaveRequestRepository leaveRequests,
                               EmploymentContractRepository contracts,
                               ProbationReviewRepository probationReviews,
                               SettingService settings,
                               NamedParameterJdbcTemplate jdbc) {
        this.context = context;
        this.employees = employees;
        this.leaveRequests = leaveRequests;
        this.contracts = contracts;
        this.probationReviews = probationReviews;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TeamMember> directReports() {
        Employee me = context.currentEmployee();
        List<Employee> team = employees.findDirectReports(me.getId());
        if (team.isEmpty()) return List.of();

        List<UUID> ids = team.stream().map(Employee::getId).toList();
        LocalDate today = LocalDate.now();
        var onLeaveToday = leaveRequests.findApprovedOnFor(ids, today).stream()
                .map(LeaveRequest::getEmployeeId)
                .collect(Collectors.toSet());

        return team.stream()
                .map(e -> new TeamMember(
                        e.getId(),
                        e.getEmployeeNo(),
                        e.getFirstName(),
                        e.getLastName(),
                        e.getPositionTitle(),
                        e.getDepartmentName(),
                        e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name(),
                        e.getHireDate(),
                        onLeaveToday.contains(e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamSummary summary() {
        Employee me = context.currentEmployee();
        List<Employee> team = employees.findDirectReports(me.getId());
        if (team.isEmpty()) {
            return new TeamSummary(me.getId(), 0, 0, 0, 0, 0, List.of(), List.of());
        }
        List<UUID> ids = team.stream().map(Employee::getId).toList();
        LocalDate today = LocalDate.now();
        LocalDate ahead = today.plusDays(LOOKAHEAD_DAYS);

        long onLeaveToday = leaveRequests.findApprovedOnFor(ids, today).size();
        long pendingLeaves = leaveRequests.countPendingForEmployees(ids);

        List<ProbationReview> dueProbation = probationReviews.findPendingForEmployees(ids, ahead);
        List<EmploymentContract> endingContracts = contracts.findActiveExpiringForEmployees(ids, ahead);

        long onProbation = team.stream()
                .filter(e -> e.getEmploymentStatus() != null
                        && "ON_PROBATION".equals(e.getEmploymentStatus().name()))
                .count();

        return new TeamSummary(
                me.getId(),
                team.size(),
                onLeaveToday,
                onProbation,
                pendingLeaves,
                endingContracts.size(),
                dueProbation.stream()
                        .map(r -> new TeamDtos.UpcomingItem(
                                r.getEmployeeId(),
                                r.getScheduledDate(),
                                r.getReviewType().name()))
                        .toList(),
                endingContracts.stream()
                        .map(c -> new TeamDtos.UpcomingItem(
                                c.getEmployeeId(),
                                c.getEndDate(),
                                "CONTRACT " + c.getContractNo()))
                        .toList());
    }

    /**
     * M433 — Team compensation: current salaries for direct reports.
     * Requires manager_can_view_salary setting; otherwise throws AccessDeniedException.
     */
    @Transactional(readOnly = true)
    public List<TeamDtos.TeamCompensation> teamCompensation() {
        if (!settings.managerCanViewSalary()) {
            throw new AccessDeniedException("Manager compensation view is disabled");
        }

        Employee me = context.currentEmployee();
        List<Employee> team = employees.findDirectReports(me.getId());
        if (team.isEmpty()) return List.of();

        List<UUID> ids = team.stream().map(Employee::getId).toList();

        // Get current salaries via JDBC (latest effective_from where effectiveTo is null or future)
        String sql = """
            SELECT
                e.id AS employee_id,
                e.employee_no,
                e.first_name || ' ' || e.last_name AS full_name,
                e.position_title,
                c.monthly_base_salary,
                c.currency
            FROM core_hr.employee e
            LEFT JOIN LATERAL (
                SELECT monthly_base_salary, currency
                FROM payroll.employee_compensation
                WHERE employee_id = e.id
                  AND effective_from <= CURRENT_DATE
                  AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
                ORDER BY effective_from DESC
                LIMIT 1
            ) c ON true
            WHERE e.id = ANY(:ids)
            ORDER BY e.employee_no
            """;

        return jdbc.query(sql, Map.of("ids", ids.toArray(new UUID[0])), (rs, i) ->
                new TeamDtos.TeamCompensation(
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getString("position_title"),
                        rs.getBigDecimal("monthly_base_salary"),
                        rs.getString("currency")
                ));
    }
}
