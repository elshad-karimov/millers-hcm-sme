package az.millers.hcm.performance.service;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.performance.domain.SuccessionPlan;
import az.millers.hcm.performance.repo.SuccessionPlanRepository;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * HCM_16 M413 — succession plan approval (PRD §16.4). Pattern:
 * submit → workflow start (SUCCESSION_PLAN_APPROVAL), AFTER_COMMIT listener
 * in REQUIRES_NEW tx (copied from GoalApprovalService M392).
 */
@Service
public class SuccessionPlanApprovalService {

    public static final String WORKFLOW_DEFINITION = "SUCCESSION_PLAN_APPROVAL";
    public static final String SUBJECT_ENTITY = "SuccessionPlan";

    private static final Logger log = LoggerFactory.getLogger(SuccessionPlanApprovalService.class);
    private static final String MODULE = "SUCCESSION";

    private final SuccessionPlanRepository plans;
    private final WorkflowService workflows;
    private final AuditService audit;
    private final TransactionTemplate requiresNew;

    public SuccessionPlanApprovalService(SuccessionPlanRepository plans,
                                         WorkflowService workflows,
                                         AuditService audit,
                                         PlatformTransactionManager txManager) {
        this.plans = plans;
        this.workflows = workflows;
        this.audit = audit;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Submit plan for approval → start SUCCESSION_PLAN_APPROVAL workflow. */
    @Transactional
    public SuccessionPlan submit(UUID planId, String submittedBy) {
        SuccessionPlan plan = plans.findById(planId)
                .orElseThrow(() -> new az.millers.hcm.common.ResourceNotFoundException("Plan not found: " + planId));
        if (!"DRAFT".equals(plan.getStatus())) {
            throw new BadRequestException("Only DRAFT plans can be submitted (current: " + plan.getStatus() + ")");
        }

        // Start workflow (subject entity = SuccessionPlan, subjectId = plan.id).
        StartWorkflowRequest req = new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                SUBJECT_ENTITY,
                plan.getId().toString(),
                "Succession Plan Approval",
                Map.of("planOwner", plan.getPlanOwnerEmployeeId().toString(),
                        "criticalPosition", plan.getCriticalPositionId().toString())
        );
        az.millers.hcm.workflow.domain.WorkflowInstance instance = workflows.start(req);

        plan.setWorkflowInstanceId(instance.getId());
        plan.setStatus("SUBMITTED");
        SuccessionPlan saved = plans.save(plan);
        audit.record(MODULE, "SuccessionPlan", planId.toString(), "SUBMIT", "DRAFT", "SUBMITTED");
        return saved;
    }

    /**
     * AFTER_COMMIT listener for workflow completion. APPROVED → plan APPROVED,
     * REJECTED → back to DRAFT. REQUIRES_NEW tx to avoid silent discard
     * (pattern copied from GoalApprovalService).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!SUBJECT_ENTITY.equals(event.subjectEntity())) {
            return;
        }
        try {
            UUID planId = UUID.fromString(event.subjectId());
            requiresNew.executeWithoutResult(tx -> {
                plans.findById(planId).ifPresent(plan -> {
                    String before = plan.getStatus();
                    if (WorkflowStatus.APPROVED == event.status()) {
                        plan.setStatus("APPROVED");
                    } else if (WorkflowStatus.REJECTED == event.status()
                            || WorkflowStatus.CANCELLED == event.status()) {
                        plan.setStatus("DRAFT");
                        plan.setWorkflowInstanceId(null);
                    }
                    plans.save(plan);
                    audit.record(MODULE, "SuccessionPlan", planId.toString(),
                            "WORKFLOW", before, plan.getStatus());
                });
            });
        } catch (Exception ex) {
            log.error("Failed to handle succession-plan workflow completion for {}: {}",
                    event.subjectId(), ex.getMessage(), ex);
        }
    }
}
