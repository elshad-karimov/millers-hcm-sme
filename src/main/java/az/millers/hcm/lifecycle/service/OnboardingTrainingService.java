package az.millers.hcm.lifecycle.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import az.millers.hcm.learning.api.dto.EnrollRequest;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.AssignRequest;
import az.millers.hcm.learning.domain.EnrolledVia;
import az.millers.hcm.learning.domain.Enrollment;
import az.millers.hcm.learning.event.CoursePassedEvent;
import az.millers.hcm.learning.service.EnrollmentService;
import az.millers.hcm.learning.service.LearningPathAssignmentService;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.UpdateTaskRequest;
import az.millers.hcm.lifecycle.domain.ChecklistAssignmentStatus;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.ChecklistTaskType;
import az.millers.hcm.lifecycle.domain.TrainingTargetKind;
import az.millers.hcm.lifecycle.event.ChecklistStartedEvent;
import az.millers.hcm.lifecycle.repo.ChecklistTaskStatusRepository;

/**
 * Wires onboarding TRAINING_ASSIGNMENT tasks to the LMS (M303 — Phase B.3).
 *
 * <p>On checklist start, each training task that targets a course or learning
 * path auto-enrols / auto-assigns the new hire (reusing {@code EnrollmentService}
 * / {@code LearningPathAssignmentService}) and moves the task to IN_PROGRESS.
 * When the LMS later publishes {@link CoursePassedEvent}, the matching onboarding
 * task is marked DONE — closing the loop assign → complete.
 *
 * <p>Both handlers run {@code AFTER_COMMIT} — after the publishing transaction
 * has committed. Each task's work is wrapped in a fresh REQUIRES_NEW
 * transaction (via {@link TransactionTemplate}); this is mandatory because a
 * plain {@code @Transactional} call inside an AFTER_COMMIT listener would join
 * the already-committed transaction and its writes would be silently discarded.
 * Per-task isolation also means one bad task (already enrolled, unpublished
 * course, inactive path) is caught and logged without affecting the hire, the
 * checklist start, or the other tasks.
 */
@Service
public class OnboardingTrainingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingTrainingService.class);

    private final ChecklistTaskStatusRepository taskStatuses;
    private final ChecklistService checklistService;
    private final EnrollmentService enrollmentService;
    private final LearningPathAssignmentService pathAssignments;
    /** Fresh transaction per task — see class doc on the AFTER_COMMIT gotcha. */
    private final TransactionTemplate requiresNew;

    public OnboardingTrainingService(ChecklistTaskStatusRepository taskStatuses,
                                     ChecklistService checklistService,
                                     EnrollmentService enrollmentService,
                                     LearningPathAssignmentService pathAssignments,
                                     PlatformTransactionManager txManager) {
        this.taskStatuses = taskStatuses;
        this.checklistService = checklistService;
        this.enrollmentService = enrollmentService;
        this.pathAssignments = pathAssignments;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Auto-enrol / auto-assign training targets when an onboarding checklist starts. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChecklistStarted(ChecklistStartedEvent e) {
        if (e.flowType() != ChecklistFlowType.ONBOARDING) return;
        for (ChecklistTaskStatus ts : taskStatuses.findByAssignmentIdOrderByStepOrderAsc(e.assignmentId())) {
            if (ts.getTaskType() != ChecklistTaskType.TRAINING_ASSIGNMENT) continue;
            if (ts.getTrainingTargetKind() == null || ts.getTrainingTargetId() == null) continue;
            try {
                requiresNew.executeWithoutResult(status -> {
                    if (ts.getTrainingTargetKind() == TrainingTargetKind.COURSE) {
                        Enrollment enr = enrollmentService.enroll(new EnrollRequest(
                                ts.getTrainingTargetId(), e.employeeId(), EnrolledVia.ASSIGNED, null));
                        markInProgress(ts.getId(), "Auto-enrolled in course (" + enr.getEnrollmentNo() + ")");
                        log.info("Onboarding: enrolled employee {} in course {} ({})",
                                e.employeeId(), ts.getTrainingTargetId(), enr.getEnrollmentNo());
                    } else {
                        pathAssignments.assign(ts.getTrainingTargetId(), new AssignRequest(
                                e.employeeId(), null, "Auto-assigned on onboarding"));
                        markInProgress(ts.getId(), "Auto-assigned learning path");
                        log.info("Onboarding: assigned learning path {} to employee {}",
                                ts.getTrainingTargetId(), e.employeeId());
                    }
                });
            } catch (RuntimeException ex) {
                // Already enrolled / unpublished course / inactive path — non-fatal.
                log.warn("Onboarding training auto-assign skipped for task {} (target {}): {}",
                        ts.getId(), ts.getTrainingTargetId(), ex.getMessage());
            }
        }
    }

    /** Close the loop: when a course is passed, mark the matching onboarding task DONE. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCoursePassed(CoursePassedEvent e) {
        List<ChecklistTaskStatus> tasks = taskStatuses.findOpenTrainingTasks(
                e.employeeId(), ChecklistFlowType.ONBOARDING, ChecklistAssignmentStatus.IN_PROGRESS,
                ChecklistTaskType.TRAINING_ASSIGNMENT, e.courseId(),
                List.of(ChecklistTaskStatusValue.PENDING, ChecklistTaskStatusValue.IN_PROGRESS));
        for (ChecklistTaskStatus ts : tasks) {
            try {
                requiresNew.executeWithoutResult(status ->
                        checklistService.updateTask(ts.getId(), new UpdateTaskRequest(
                                ChecklistTaskStatusValue.DONE, null, null, "Course passed")));
                log.info("Onboarding: marked training task {} DONE after employee {} passed course {}",
                        ts.getId(), e.employeeId(), e.courseId());
            } catch (RuntimeException ex) {
                log.warn("Could not auto-complete onboarding training task {}: {}",
                        ts.getId(), ex.getMessage());
            }
        }
    }

    private void markInProgress(java.util.UUID taskStatusId, String note) {
        checklistService.updateTask(taskStatusId, new UpdateTaskRequest(
                ChecklistTaskStatusValue.IN_PROGRESS, null, null, note));
    }
}
