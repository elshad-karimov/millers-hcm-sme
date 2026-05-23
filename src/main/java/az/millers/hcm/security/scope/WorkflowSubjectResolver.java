package az.millers.hcm.security.scope;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import az.millers.hcm.businesstrip.repo.BusinessTripRequestRepository;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.lifecycle.repo.ContractChangeRepository;
import az.millers.hcm.lifecycle.repo.TerminationRequestRepository;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.permission.repo.PermissionRequestRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * Resolves a workflow subject (`(entity, subjectId)` pair on a
 * {@code workflow_instance}) to the owning {@code employee_id}, so
 * {@link AccessScopeService} can decide whether the current user is
 * allowed to see / act on the approval task.
 *
 * <p>Used by the workflow inbox filter (PRD 14.9). Returns empty when
 * the entity has no single employee owner (e.g. {@code PayrollRun},
 * {@code OrgVersion} — those are org-wide and rely on role checks
 * alone). Returns empty if the subject id is malformed or the row no
 * longer exists.
 */
@Component
public class WorkflowSubjectResolver {

    private final LeaveRequestRepository leave;
    private final PermissionRequestRepository permission;
    private final BusinessTripRequestRepository businessTrip;
    private final TimesheetRepository timesheet;
    private final TerminationRequestRepository termination;
    private final ContractChangeRepository contractChange;
    private final PerformanceReviewRepository performanceReview;

    public WorkflowSubjectResolver(LeaveRequestRepository leave,
                                    PermissionRequestRepository permission,
                                    BusinessTripRequestRepository businessTrip,
                                    TimesheetRepository timesheet,
                                    TerminationRequestRepository termination,
                                    ContractChangeRepository contractChange,
                                    PerformanceReviewRepository performanceReview) {
        this.leave = leave;
        this.permission = permission;
        this.businessTrip = businessTrip;
        this.timesheet = timesheet;
        this.termination = termination;
        this.contractChange = contractChange;
        this.performanceReview = performanceReview;
    }

    /**
     * @return the employee id behind a workflow subject, or empty when
     *         the entity isn't employee-scoped or the row can't be
     *         resolved.
     */
    public Optional<UUID> resolveEmployeeId(String entity, String subjectIdStr) {
        if (entity == null || subjectIdStr == null) return Optional.empty();
        UUID subjectId;
        try {
            subjectId = UUID.fromString(subjectIdStr);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return switch (entity) {
            case "LeaveRequest"        -> leave.findById(subjectId).map(r -> r.getEmployeeId());
            case "PermissionRequest"   -> permission.findById(subjectId).map(r -> r.getEmployeeId());
            case "BusinessTripRequest" -> businessTrip.findById(subjectId).map(r -> r.getEmployeeId());
            case "Timesheet"           -> timesheet.findById(subjectId).map(r -> r.getEmployeeId());
            case "TerminationRequest"  -> termination.findById(subjectId).map(r -> r.getEmployeeId());
            case "ContractChange"      -> contractChange.findById(subjectId).map(r -> r.getEmployeeId());
            case "PerformanceReview"   -> performanceReview.findById(subjectId).map(r -> r.getEmployeeId());
            // Org-wide / batch subjects (OrgVersion, PayrollRun, …) are
            // gated by role alone — no employee dimension to filter on.
            default                    -> Optional.empty();
        };
    }

    /** True iff the entity is one we know how to scope on. */
    public boolean isEmployeeScoped(String entity) {
        return entity != null && switch (entity) {
            case "LeaveRequest", "PermissionRequest", "BusinessTripRequest",
                 "Timesheet", "TerminationRequest", "ContractChange",
                 "PerformanceReview" -> true;
            default -> false;
        };
    }
}
