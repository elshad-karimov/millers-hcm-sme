package az.millers.hcm.selfservice.timeline;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.domain.AuditLog;
import az.millers.hcm.audit.repo.AuditLogRepository;
import az.millers.hcm.businesstrip.repo.BusinessTripRequestRepository;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.repo.EmployeeRewardRepository;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.lifecycle.repo.DisciplinaryActionRepository;
import az.millers.hcm.lifecycle.repo.EmploymentContractRepository;
import az.millers.hcm.lifecycle.repo.ProbationReviewRepository;
import az.millers.hcm.lifecycle.repo.TerminationRequestRepository;
import az.millers.hcm.permission.repo.PermissionRequestRepository;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.selfservice.timeline.TimelineEvent.TimelineKind;

/**
 * Aggregates lifecycle events for one employee from many sources into a single
 * chronological feed (M76 / P2-27/28).
 *
 * <p>Sources unified here:
 * <ul>
 *   <li>Synthesised HIRE event from {@code Employee.hireDate}</li>
 *   <li>Employment contracts — signed + ended</li>
 *   <li>Termination requests</li>
 *   <li>Leave / business-trip / permission submissions</li>
 *   <li>Disciplinary actions</li>
 *   <li>Probation reviews — scheduled + completed (two separate events)</li>
 *   <li>Rewards</li>
 *   <li>Employee-row audit log entries for STATUS_CHANGE-class actions</li>
 * </ul>
 *
 * <p>No new tables — every read uses an existing repository method. Scope
 * checks delegate to {@link AccessScopeService} so managers / scoped HR
 * specialists only see their team's timelines.
 */
@Service
public class EmployeeTimelineService {

    private static final int DEFAULT_LIMIT = 200;
    /** Local-time anchor for date-only events (so they sort sensibly). */
    private static final LocalTime DAY_ANCHOR = LocalTime.of(9, 0);

    private final EmployeeRepository employees;
    private final EmploymentContractRepository contracts;
    private final TerminationRequestRepository terminations;
    private final LeaveRequestRepository leaveRequests;
    private final BusinessTripRequestRepository businessTrips;
    private final PermissionRequestRepository permissionRequests;
    private final DisciplinaryActionRepository disciplinary;
    private final ProbationReviewRepository probationReviews;
    private final EmployeeRewardRepository rewards;
    private final AuditLogRepository auditLogs;
    private final AccessScopeService scope;

    public EmployeeTimelineService(EmployeeRepository employees,
                                    EmploymentContractRepository contracts,
                                    TerminationRequestRepository terminations,
                                    LeaveRequestRepository leaveRequests,
                                    BusinessTripRequestRepository businessTrips,
                                    PermissionRequestRepository permissionRequests,
                                    DisciplinaryActionRepository disciplinary,
                                    ProbationReviewRepository probationReviews,
                                    EmployeeRewardRepository rewards,
                                    AuditLogRepository auditLogs,
                                    AccessScopeService scope) {
        this.employees = employees;
        this.contracts = contracts;
        this.terminations = terminations;
        this.leaveRequests = leaveRequests;
        this.businessTrips = businessTrips;
        this.permissionRequests = permissionRequests;
        this.disciplinary = disciplinary;
        this.probationReviews = probationReviews;
        this.rewards = rewards;
        this.auditLogs = auditLogs;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<TimelineEvent> forEmployee(UUID employeeId, int limit) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + employeeId));
        if (!scope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }

        List<TimelineEvent> out = new ArrayList<>();

        // Hire — synthesised from hireDate.
        if (employee.getHireDate() != null) {
            out.add(TimelineEvent.of(
                    atDayAnchor(employee.getHireDate()),
                    TimelineKind.HIRE,
                    "Hired",
                    "Joined the company",
                    employee.getCreatedBy(),
                    employee.getId()));
        }

        // Contracts (start + end if closed).
        contracts.findByEmployeeIdOrderByStartDateDesc(employeeId).forEach(c -> {
            out.add(TimelineEvent.of(
                    atDayAnchor(c.getStartDate()),
                    TimelineKind.CONTRACT_SIGNED,
                    "Contract " + c.getContractNo() + " started",
                    c.getContractType() == null ? null : c.getContractType().name(),
                    null,
                    c.getId()));
            if (c.getEndDate() != null) {
                out.add(TimelineEvent.of(
                        atDayAnchor(c.getEndDate()),
                        TimelineKind.CONTRACT_ENDED,
                        "Contract " + c.getContractNo() + " ended",
                        c.getStatus() == null ? null : c.getStatus().name(),
                        null,
                        c.getId()));
            }
        });

        // Terminations (use lastWorkingDate when available, else createdAt).
        terminations.findByEmployeeIdOrderByCreatedAtDesc(employeeId, PageRequest.of(0, limit))
                .forEach(t -> out.add(TimelineEvent.of(
                        t.getLastWorkingDate() != null
                                ? atDayAnchor(t.getLastWorkingDate())
                                : t.getCreatedAt(),
                        TimelineKind.TERMINATION,
                        "Termination " + t.getTerminationNo(),
                        t.getReasonCode() == null ? null : t.getReasonCode().name(),
                        null,
                        t.getId())));

        // Leave requests.
        leaveRequests.findByEmployeeIdOrderByStartDateDesc(employeeId, PageRequest.of(0, limit))
                .forEach(r -> out.add(TimelineEvent.of(
                        atDayAnchor(r.getStartDate()),
                        TimelineKind.LEAVE_REQUEST,
                        "Leave " + r.getRequestNo()
                                + " (" + r.getStartDate() + " → " + r.getEndDate() + ")",
                        r.getStatus() == null ? null : r.getStatus().name(),
                        null,
                        r.getId())));

        // Business trips.
        businessTrips.findByEmployeeIdOrderByStartDateDesc(employeeId, PageRequest.of(0, limit))
                .forEach(t -> out.add(TimelineEvent.of(
                        atDayAnchor(t.getStartDate()),
                        TimelineKind.BUSINESS_TRIP,
                        "Trip " + t.getTripNo() + " — "
                                + (t.getDestinationCity() == null ? "?" : t.getDestinationCity()),
                        t.getPurpose(),
                        null,
                        t.getId())));

        // Permission requests.
        permissionRequests.findByEmployeeIdOrderByPermissionDateDesc(
                        employeeId, PageRequest.of(0, limit))
                .forEach(p -> out.add(TimelineEvent.of(
                        atDayAnchor(p.getPermissionDate()),
                        TimelineKind.PERMISSION,
                        "Permission " + p.getRequestNo(),
                        p.getReason(),
                        null,
                        p.getId())));

        // Disciplinary actions.
        disciplinary.findByEmployeeIdOrderByActionDateDesc(employeeId).forEach(d ->
                out.add(TimelineEvent.of(
                        atDayAnchor(d.getActionDate()),
                        TimelineKind.DISCIPLINARY,
                        "Disciplinary " + d.getActionNo()
                                + " (" + d.getActionType().name() + ")",
                        d.getStatus() == null ? null : d.getStatus().name(),
                        d.getIssuedBy(),
                        d.getId())));

        // Probation reviews — scheduled + (if completed) completion event.
        probationReviews.findByEmployeeIdOrderByScheduledDateDesc(employeeId).forEach(p -> {
            out.add(TimelineEvent.of(
                    atDayAnchor(p.getScheduledDate()),
                    TimelineKind.PROBATION_REVIEW,
                    "Probation review scheduled (" + p.getReviewType().name() + ")",
                    p.getStatus() == null ? null : p.getStatus().name(),
                    null,
                    p.getId()));
            if (p.getCompletedDate() != null) {
                out.add(TimelineEvent.of(
                        atDayAnchor(p.getCompletedDate()),
                        TimelineKind.PROBATION_REVIEW,
                        "Probation review completed (" + p.getReviewType().name() + ")",
                        p.getOutcome() == null ? null : p.getOutcome().name(),
                        null,
                        p.getId()));
            }
        });

        // Rewards.
        rewards.findByEmployeeIdOrderByAwardedAtDesc(employeeId).forEach(r ->
                out.add(TimelineEvent.of(
                        atDayAnchor(r.getAwardedAt()),
                        TimelineKind.REWARD,
                        r.getTitle(),
                        r.getRewardType().name(),
                        r.getAwardedBy(),
                        r.getId())));

        // Employee-row audit entries — capture status changes + edits.
        auditLogs.findByEntityNameAndEntityIdOrderByCreatedAtDesc(
                        "Employee", employeeId.toString())
                .stream()
                .map(this::auditToTimeline)
                .forEach(out::add);

        out.sort(Comparator.comparing(TimelineEvent::at).reversed());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    @Transactional(readOnly = true)
    public List<TimelineEvent> forEmployee(UUID employeeId) {
        return forEmployee(employeeId, DEFAULT_LIMIT);
    }

    private TimelineEvent auditToTimeline(AuditLog log) {
        TimelineKind kind = switch (log.getAction()) {
            case "STATUS_CHANGE" -> TimelineKind.STATUS_CHANGE;
            case "TERMINATE" -> TimelineKind.TERMINATION;
            default -> TimelineKind.AUDIT_OTHER;
        };
        return TimelineEvent.of(
                log.getCreatedAt(),
                kind,
                log.getAction(),
                log.getModule(),
                log.getActor(),
                null);
    }

    private static OffsetDateTime atDayAnchor(LocalDate d) {
        if (d == null) return null;
        return OffsetDateTime.of(d, DAY_ANCHOR, ZoneOffset.UTC);
    }
}
