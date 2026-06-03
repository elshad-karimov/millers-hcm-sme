package az.millers.hcm.learning.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.AssignRequest;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.AssignmentResponse;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.CancelRequest;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.StepProgress;
import az.millers.hcm.learning.domain.Course;
import az.millers.hcm.learning.domain.Enrollment;
import az.millers.hcm.learning.domain.EnrollmentStatus;
import az.millers.hcm.learning.domain.LearningPath;
import az.millers.hcm.learning.domain.LearningPathAssignment;
import az.millers.hcm.learning.domain.LearningPathCourse;
import az.millers.hcm.learning.domain.PathAssignmentStatus;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.EnrollmentRepository;
import az.millers.hcm.learning.repo.LearningPathAssignmentRepository;
import az.millers.hcm.learning.repo.LearningPathCourseRepository;
import az.millers.hcm.learning.repo.LearningPathRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Learning path assignments — Individual Development Plans (M95).
 *
 * <p>A path template is abstract; an assignment makes it actionable for
 * one employee. Per-step progress is NOT stored on the assignment row:
 * it's derived at read time from existing {@code Enrollment} records,
 * so there is exactly one source of truth for "has this course been
 * passed". Status auto-rolls forward to {@code IN_PROGRESS} when any
 * step's enrolment status is non-trivial, and to {@code COMPLETED} when
 * every {@code requiredToAdvance} step has been PASSED.
 */
@Service
public class LearningPathAssignmentService {

    private static final String MODULE = "LEARNING";
    private static final String ENTITY = "LearningPathAssignment";

    /** Statuses that prevent re-assigning the same (path, employee). */
    private static final List<PathAssignmentStatus> ACTIVE_STATUSES =
            List.of(PathAssignmentStatus.ASSIGNED, PathAssignmentStatus.IN_PROGRESS);

    private final LearningPathAssignmentRepository assignments;
    private final LearningPathRepository paths;
    private final LearningPathCourseRepository steps;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LearningPathAssignmentService(LearningPathAssignmentRepository assignments,
                                          LearningPathRepository paths,
                                          LearningPathCourseRepository steps,
                                          EnrollmentRepository enrollments,
                                          CourseRepository courses,
                                          EmployeeRepository employees,
                                          AuditService audit,
                                          CurrentRequest currentRequest) {
        this.assignments = assignments;
        this.paths = paths;
        this.steps = steps;
        this.enrollments = enrollments;
        this.courses = courses;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    @Transactional
    public AssignmentResponse assign(UUID pathId, AssignRequest req) {
        if (req.employeeId() == null) {
            throw new BadRequestException("employeeId is required");
        }
        LearningPath path = paths.findById(pathId)
                .orElseThrow(() -> new ResourceNotFoundException("Learning path not found: " + pathId));
        if (!path.isActive()) {
            throw new BadRequestException("Cannot assign an inactive learning path");
        }
        // Partial unique index handles the race; the explicit check gives a
        // friendlier error message.
        assignments.findFirstByPathIdAndEmployeeIdAndStatusIn(
                        pathId, req.employeeId(), ACTIVE_STATUSES)
                .ifPresent(existing -> {
                    throw new BadRequestException(
                            "Employee already has an active assignment for this path");
                });

        LearningPathAssignment a = new LearningPathAssignment();
        a.setPathId(pathId);
        a.setEmployeeId(req.employeeId());
        a.setTargetCompletionDate(req.targetCompletionDate());
        a.setNotes(req.notes());
        a.setAssignedBy(currentRequest.username());
        LearningPathAssignment saved = assignments.save(a);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "ASSIGN",
                null,
                Map.of(
                        "pathId", pathId.toString(),
                        "employeeId", req.employeeId().toString(),
                        "target", req.targetCompletionDate() == null
                                ? "" : req.targetCompletionDate().toString()));
        return toResponse(saved);
    }

    @Transactional
    public AssignmentResponse cancel(UUID assignmentId, CancelRequest req) {
        LearningPathAssignment a = mustFind(assignmentId);
        if (a.getStatus() == PathAssignmentStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a COMPLETED assignment");
        }
        if (a.getStatus() == PathAssignmentStatus.CANCELLED) {
            throw new BadRequestException("Already cancelled");
        }
        a.setStatus(PathAssignmentStatus.CANCELLED);
        a.setCancelledAt(OffsetDateTime.now());
        a.setCancellationReason(req == null ? null : req.reason());
        assignments.save(a);
        audit.record(MODULE, ENTITY, assignmentId.toString(), "CANCEL",
                null, Map.of("reason", req == null || req.reason() == null ? "" : req.reason()));
        return toResponse(a);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AssignmentResponse get(UUID assignmentId) {
        return toResponse(mustFind(assignmentId));
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> forEmployee(UUID employeeId) {
        return assignments.findByEmployeeIdOrderByAssignedAtDesc(employeeId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> forPath(UUID pathId) {
        return assignments.findByPathIdOrderByAssignedAtDesc(pathId).stream()
                .map(this::toResponse).toList();
    }

    // ── Status auto-roll (called on read) ────────────────────────────────────

    /**
     * Derive progress for {@code a} and auto-advance status if warranted.
     * Status writes happen lazily on read so callers don't need to remember
     * to refresh after they Pass a course — the next view of the assignment
     * picks up the latest enrolment state.
     *
     * <p>Public so {@code SuccessionPlanService} (M96 drill-down) can reuse
     * the canonical progress computation without duplicating it.
     */
    public AssignmentResponse toResponse(LearningPathAssignment a) {
        List<LearningPathCourse> pathSteps =
                steps.findByPathIdOrderByStepOrderAsc(a.getPathId());
        // Pre-load each step's enrolment for this employee (NOT_STARTED if
        // there is no Enrollment row yet).
        Map<UUID, Optional<Enrollment>> enrolByCourse = new HashMap<>();
        for (LearningPathCourse s : pathSteps) {
            enrolByCourse.put(s.getCourseId(),
                    enrollments.findByCourseIdAndEmployeeId(s.getCourseId(), a.getEmployeeId()));
        }

        int total = pathSteps.size();
        int completed = 0;
        int requiredTotal = 0;
        int requiredCompleted = 0;
        boolean anyStarted = false;

        List<StepProgress> progress = new ArrayList<>(total);
        for (LearningPathCourse s : pathSteps) {
            Optional<Enrollment> e = enrolByCourse.get(s.getCourseId());
            EnrollmentStatus status = e.map(Enrollment::getStatus).orElse(null);
            boolean stepDone = status == EnrollmentStatus.PASSED;
            if (stepDone) completed++;
            if (s.isRequiredToAdvance()) {
                requiredTotal++;
                if (stepDone) requiredCompleted++;
            }
            if (status != null && status != EnrollmentStatus.WITHDRAWN) {
                anyStarted = true;
            }
            Course c = courses.findById(s.getCourseId()).orElse(null);
            progress.add(new StepProgress(
                    s.getId(), s.getStepOrder(), s.getCourseId(),
                    c == null ? null : c.getCode(),
                    c == null ? null : c.getTitle(),
                    s.isRequiredToAdvance(),
                    status == null ? "NOT_STARTED" : status.name(),
                    stepDone));
        }

        // Auto-advance status. Order matters: COMPLETED takes priority over
        // IN_PROGRESS, and we never demote a terminal status.
        PathAssignmentStatus current = a.getStatus();
        if (current == PathAssignmentStatus.ASSIGNED || current == PathAssignmentStatus.IN_PROGRESS) {
            if (requiredTotal > 0 && requiredCompleted >= requiredTotal) {
                a.setStatus(PathAssignmentStatus.COMPLETED);
                a.setCompletedAt(OffsetDateTime.now());
                assignments.save(a);
                audit.record(MODULE, ENTITY, a.getId().toString(), "AUTO_COMPLETE",
                        null, Map.of("completedSteps", completed, "totalSteps", total));
            } else if (anyStarted && current == PathAssignmentStatus.ASSIGNED) {
                a.setStatus(PathAssignmentStatus.IN_PROGRESS);
                assignments.save(a);
            }
        }

        int progressPct = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);

        Employee emp = employees.findById(a.getEmployeeId()).orElse(null);
        String empName = emp == null ? null :
                ((emp.getFirstName() == null ? "" : emp.getFirstName()) + " "
                        + (emp.getLastName() == null ? "" : emp.getLastName())).trim();
        String pathName = paths.findById(a.getPathId()).map(LearningPath::getName).orElse(null);

        return new AssignmentResponse(
                a.getId(), a.getPathId(), pathName,
                a.getEmployeeId(), empName,
                a.getStatus(),
                a.getAssignedAt(), a.getAssignedBy(),
                a.getTargetCompletionDate(),
                a.getCompletedAt(), a.getCancelledAt(),
                a.getCancellationReason(), a.getNotes(),
                total, completed, requiredTotal, requiredCompleted, progressPct,
                progress);
    }

    private LearningPathAssignment mustFind(UUID id) {
        return assignments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
    }

    // ── Test access ─────────────────────────────────────────────────────────

    /** Test-only: expose the progress-percent helper without touching state. */
    static int progressPercentOf(int completed, int total) {
        if (total == 0) return 0;
        return (int) Math.round(completed * 100.0 / total);
    }
}
