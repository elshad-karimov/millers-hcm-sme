package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.util.List;
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
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.domain.Goal;
import az.millers.hcm.performance.domain.GoalStatus;
import az.millers.hcm.performance.repo.GoalRepository;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * HCM_12 M392 — goal-plan approval (PRD §6.2, Employee→Manager).
 *
 * <p>Submission covers the employee's whole DRAFT goal plan for a cycle in ONE
 * GOAL_APPROVAL workflow instance (subject entity {@code GoalPlan}, subject id =
 * the employee id so {@code resolves_to_manager} routing works). §37.5 is enforced
 * at submit time: the weights of the employee's non-cancelled goals in the cycle
 * must total exactly 100.
 *
 * <p>APPROVED → every covered goal becomes ACTIVE / approval APPROVED;
 * REJECTED/RETURNED → goals stay DRAFT / approval REJECTED (rework + resubmit);
 * CANCELLED → back to NOT_SUBMITTED.
 */
@Service
public class GoalApprovalService {

    public static final String WORKFLOW_DEFINITION = "GOAL_APPROVAL";
    public static final String SUBJECT_ENTITY = "GoalPlan";

    private static final Logger log = LoggerFactory.getLogger(GoalApprovalService.class);
    private static final String MODULE = "PERFORMANCE";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final GoalRepository goals;
    private final EmployeeRepository employees;
    private final WorkflowService workflows;
    private final AuditService audit;
    private final az.millers.hcm.notifications.NotificationService notifications;
    private final TransactionTemplate requiresNew;

    public GoalApprovalService(GoalRepository goals,
                               EmployeeRepository employees,
                               WorkflowService workflows,
                               AuditService audit,
                               az.millers.hcm.notifications.NotificationService notifications,
                               PlatformTransactionManager txManager) {
        this.goals = goals;
        this.employees = employees;
        this.workflows = workflows;
        this.audit = audit;
        this.notifications = notifications;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** M402 — in-app note to the plan's employee; never fatal. */
    private void notifyEmployee(String subjectId, String title, String body) {
        try {
            employees.findById(UUID.fromString(subjectId))
                    .map(Employee::getUsername)
                    .filter(u -> u != null && !u.isBlank())
                    .ifPresent(u -> notifications.notifyAll(u, title, body,
                            MODULE, SUBJECT_ENTITY, subjectId));
        } catch (Exception ex) {
            log.warn("Goal-plan notification failed for {}: {}", subjectId, ex.getMessage());
        }
    }

    /** Submit the employee's DRAFT goal plan for the cycle for manager approval. */
    @Transactional
    public List<Goal> submit(UUID cycleId, UUID employeeId) {
        List<Goal> plan = goals.findByCycleIdAndEmployeeIdOrderByCreatedAt(cycleId, employeeId).stream()
                .filter(g -> g.getStatus() != GoalStatus.CANCELLED)
                .toList();
        if (plan.isEmpty()) {
            throw new BadRequestException("No goals to submit for this employee and cycle");
        }
        if (plan.stream().anyMatch(g -> "PENDING_APPROVAL".equals(g.getApprovalStatus()))) {
            throw new BadRequestException("The goal plan is already awaiting approval");
        }
        List<Goal> drafts = plan.stream()
                .filter(g -> g.getStatus() == GoalStatus.DRAFT
                        && !"APPROVED".equals(g.getApprovalStatus()))
                .toList();
        if (drafts.isEmpty()) {
            throw new BadRequestException("No DRAFT goals awaiting submission (already approved or active)");
        }
        // §37.5 — the plan's weights must total exactly 100 at submission.
        BigDecimal weightSum = plan.stream()
                .map(Goal::getWeightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightSum.compareTo(HUNDRED) != 0) {
            throw new BadRequestException(
                    "Goal weights must total exactly 100 to submit (currently " + weightSum.stripTrailingZeros().toPlainString() + ")");
        }

        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new BadRequestException("Employee not found: " + employeeId));
        String who = (emp.getFirstName() + " " + emp.getLastName()).trim();

        WorkflowInstance instance = workflows.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION, MODULE, SUBJECT_ENTITY, employeeId.toString(),
                "Goal plan: " + who + " (" + drafts.size() + " goals, weights 100%)",
                Map.of(
                        "cycleId", cycleId.toString(),
                        "employeeId", employeeId.toString(),
                        "goalCount", String.valueOf(drafts.size())
                )));

        for (Goal g : drafts) {
            g.setApprovalStatus("PENDING_APPROVAL");
            g.setWorkflowInstanceId(instance.getId());
        }
        List<Goal> saved = goals.saveAll(drafts);
        audit.record(MODULE, SUBJECT_ENTITY, employeeId.toString(), "SUBMIT",
                null, Map.of("cycleId", cycleId.toString(),
                        "workflowInstanceId", instance.getId().toString(),
                        "goals", String.valueOf(drafts.size())));
        return saved;
    }

    /**
     * Workflow completion listener (AFTER_COMMIT + REQUIRES_NEW — the M303/M377
     * pattern; plain REQUIRED work after commit is silently discarded).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!SUBJECT_ENTITY.equals(event.subjectEntity())) return;

        requiresNew.executeWithoutResult(status -> {
            List<Goal> plan = goals.findByWorkflowInstanceId(event.instanceId()).stream()
                    .filter(g -> "PENDING_APPROVAL".equals(g.getApprovalStatus()))   // idempotent
                    .toList();
            if (plan.isEmpty()) return;

            switch (event.status()) {
                case APPROVED, AUTO_APPROVED -> {
                    for (Goal g : plan) {
                        g.setApprovalStatus("APPROVED");
                        if (g.getStatus() == GoalStatus.DRAFT) g.setStatus(GoalStatus.ACTIVE);
                    }
                    goals.saveAll(plan);
                    audit.record(MODULE, SUBJECT_ENTITY, event.subjectId(), "APPROVED",
                            null, Map.of("goals", String.valueOf(plan.size())));
                    notifyEmployee(event.subjectId(), "Goal plan approved",
                            "Your goal plan (" + plan.size() + " goals) was approved and is now active.");
                    log.info("Goal plan {} approved — {} goals activated", event.instanceId(), plan.size());
                }
                case REJECTED, RETURNED -> {
                    for (Goal g : plan) g.setApprovalStatus("REJECTED");
                    goals.saveAll(plan);
                    audit.record(MODULE, SUBJECT_ENTITY, event.subjectId(), "REJECTED",
                            null, Map.of("goals", String.valueOf(plan.size())));
                    notifyEmployee(event.subjectId(), "Goal plan rejected",
                            "Your goal plan was rejected — review the comments, adjust and resubmit.");
                }
                case CANCELLED -> {
                    for (Goal g : plan) {
                        g.setApprovalStatus("NOT_SUBMITTED");
                        g.setWorkflowInstanceId(null);
                    }
                    goals.saveAll(plan);
                    audit.record(MODULE, SUBJECT_ENTITY, event.subjectId(), "CANCELLED",
                            null, Map.of("goals", String.valueOf(plan.size())));
                }
                default -> log.warn("Unexpected workflow status {} for goal plan {}",
                        event.status(), event.instanceId());
            }
        });
    }
}
