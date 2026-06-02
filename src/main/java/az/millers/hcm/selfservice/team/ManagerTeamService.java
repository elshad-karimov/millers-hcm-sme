package az.millers.hcm.selfservice.team;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ManagerTeamService(EmployeeContextService context,
                               EmployeeRepository employees,
                               LeaveRequestRepository leaveRequests,
                               EmploymentContractRepository contracts,
                               ProbationReviewRepository probationReviews) {
        this.context = context;
        this.employees = employees;
        this.leaveRequests = leaveRequests;
        this.contracts = contracts;
        this.probationReviews = probationReviews;
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
}
