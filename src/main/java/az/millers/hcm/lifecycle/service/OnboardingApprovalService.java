package az.millers.hcm.lifecycle.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.UpdateTaskRequest;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.ChecklistTaskType;
import az.millers.hcm.lifecycle.event.ChecklistStartedEvent;
import az.millers.hcm.lifecycle.repo.ChecklistTaskStatusRepository;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * M309 — Phase D.2: APPROVAL_TASK workflow gate.
 *
 * <p>When an onboarding checklist starts, every task of type
 * {@link ChecklistTaskType#APPROVAL_TASK} automatically spawns a
 * {@code CHECKLIST_TASK_APPROVAL} workflow instance routed to the hire's
 * direct manager ({@code resolves_to_manager = true}, 48-h SLA). The task
 * is moved to IN_PROGRESS while the approval is pending.
 *
 * <p>When the manager acts on the workflow:
 * <ul>
 *   <li>APPROVED → checklist task DONE; {@code autoCompleteIfDone} may
 *       then complete the full assignment (which publishes
 *       {@code ChecklistAssignmentCompletedEvent} and triggers M308 notifications).
 *   <li>REJECTED / any other terminal state → task returns to PENDING so HR
 *       can re-open, reassign, or manually mark it done.
 * </ul>
 *
 * <p><strong>Tx pattern for the start listener:</strong> AFTER_COMMIT with each
 * task wrapped in REQUIRES_NEW — same pattern as M303/M308.  WorkflowService.start()
 * runs in that new transaction and the security context (caller identity) is still
 * in scope because Spring events are synchronous in the same thread.
 *
 * <p><strong>Tx pattern for the completion listener:</strong> plain {@code @EventListener}
 * (not AFTER_COMMIT) so the task update commits together with the workflow state
 * change in the same transaction initiated by {@code WorkflowService.act()}.
 */
@Service
public class OnboardingApprovalService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingApprovalService.class);
    private static final String DEFINITION_CODE = "CHECKLIST_TASK_APPROVAL";

    private final ChecklistTaskStatusRepository taskStatuses;
    private final ChecklistService checklistService;
    private final WorkflowService workflowService;
    private final TransactionTemplate requiresNew;

    public OnboardingApprovalService(ChecklistTaskStatusRepository taskStatuses,
                                     ChecklistService checklistService,
                                     WorkflowService workflowService,
                                     PlatformTransactionManager txManager) {
        this.taskStatuses = taskStatuses;
        this.checklistService = checklistService;
        this.workflowService = workflowService;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * On onboarding assignment start: spawn a manager-approval workflow for
     * every APPROVAL_TASK task, then move it to IN_PROGRESS.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChecklistStarted(ChecklistStartedEvent e) {
        if (e.flowType() != ChecklistFlowType.ONBOARDING) return;
        for (ChecklistTaskStatus ts : taskStatuses.findByAssignmentIdOrderByStepOrderAsc(e.assignmentId())) {
            if (ts.getTaskType() != ChecklistTaskType.APPROVAL_TASK) continue;
            try {
                requiresNew.executeWithoutResult(status -> spawnApproval(ts));
            } catch (RuntimeException ex) {
                log.warn("Could not spawn approval workflow for checklist task {} ({}): {}",
                        ts.getId(), ts.getTitle(), ex.getMessage());
            }
        }
    }

    /**
     * On workflow completion for a {@code ChecklistTaskStatus} subject:
     * APPROVED → DONE, any other terminal state → PENDING (returnable for correction).
     *
     * <p>Plain {@code @EventListener} so it commits inside the same transaction as
     * {@code WorkflowService.act()}.
     */
    @EventListener
    public void onWorkflowCompleted(WorkflowCompletedEvent e) {
        if (!"ChecklistTaskStatus".equals(e.subjectEntity())) return;
        UUID taskStatusId;
        try {
            taskStatusId = UUID.fromString(e.subjectId());
        } catch (IllegalArgumentException ex) {
            return;
        }
        boolean approved = e.status() == WorkflowStatus.APPROVED;
        ChecklistTaskStatusValue newStatus = approved
                ? ChecklistTaskStatusValue.DONE
                : ChecklistTaskStatusValue.PENDING;
        String note = approved
                ? "Approved by manager (workflow " + e.instanceId() + ")"
                : "Returned — manager rejected (workflow " + e.instanceId() + ")";
        try {
            checklistService.updateTask(taskStatusId,
                    new UpdateTaskRequest(newStatus, null, null, note));
            log.info("Checklist approval task {} → {} (wf {})", taskStatusId, newStatus, e.instanceId());
        } catch (RuntimeException ex) {
            log.warn("Could not update checklist approval task {}: {}", taskStatusId, ex.getMessage());
        }
    }

    private void spawnApproval(ChecklistTaskStatus ts) {
        workflowService.start(new StartWorkflowRequest(
                DEFINITION_CODE,
                "lifecycle",
                "ChecklistTaskStatus",
                ts.getId().toString(),
                "Approve: " + ts.getTitle(),
                null));
        checklistService.updateTask(ts.getId(), new UpdateTaskRequest(
                ChecklistTaskStatusValue.IN_PROGRESS, null, null,
                "Pending manager approval (workflow spawned)"));
        log.info("Spawned {} workflow for checklist task {} ({})",
                DEFINITION_CODE, ts.getId(), ts.getTitle());
    }
}
