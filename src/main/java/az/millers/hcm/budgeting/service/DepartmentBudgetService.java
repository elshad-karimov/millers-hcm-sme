package az.millers.hcm.budgeting.service;

import java.time.OffsetDateTime;
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
import az.millers.hcm.budgeting.domain.BudgetCycle;
import az.millers.hcm.budgeting.domain.BudgetCycleStatus;
import az.millers.hcm.budgeting.domain.DepartmentBudget;
import az.millers.hcm.budgeting.domain.DepartmentBudgetStatus;
import az.millers.hcm.budgeting.repo.BudgetCycleRepository;
import az.millers.hcm.budgeting.repo.DepartmentBudgetRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * HCM_20 M425 — Department budget management (PRD §20.3).
 * Upsert per cycle+org-unit while cycle OPEN.
 * Submit → BUDGET_APPROVAL workflow → APPROVED/REJECTED.
 */
@Service
public class DepartmentBudgetService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentBudgetService.class);
    private static final String TENANT = "default";
    private static final String MODULE = "BUDGETING";
    private static final String ENTITY = "DepartmentBudget";
    private static final String WORKFLOW_DEFINITION = "BUDGET_APPROVAL";

    private final DepartmentBudgetRepository repo;
    private final BudgetCycleRepository cycles;
    private final WorkflowService workflowService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final TransactionTemplate requiresNew;

    public DepartmentBudgetService(DepartmentBudgetRepository repo,
                                   BudgetCycleRepository cycles,
                                   WorkflowService workflowService,
                                   AuditService audit,
                                   CurrentRequest currentRequest,
                                   PlatformTransactionManager txManager) {
        this.repo = repo;
        this.cycles = cycles;
        this.workflowService = workflowService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<DepartmentBudget> listByCycle(UUID cycleId) {
        return repo.findByCycleIdOrderByOrgUnitId(cycleId);
    }

    @Transactional(readOnly = true)
    public DepartmentBudget get(UUID id) {
        return repo.findById(id)
                .filter(b -> TENANT.equals(b.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Department budget not found: " + id));
    }

    /**
     * Upsert department budget for a cycle + org unit.
     * Only allowed while cycle is OPEN. DRAFT → DRAFT (editable).
     */
    @Transactional
    public DepartmentBudget upsert(UUID cycleId, DepartmentBudget budget) {
        BudgetCycle cycle = cycles.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget cycle not found: " + cycleId));

        if (cycle.getStatus() != BudgetCycleStatus.OPEN) {
            throw new BadRequestException("Cannot modify budgets for a non-OPEN cycle");
        }

        DepartmentBudget existing = repo.findByCycleIdAndOrgUnitId(cycleId, budget.getOrgUnitId())
                .orElse(null);

        if (existing != null) {
            // Update existing
            if (existing.getStatus() != DepartmentBudgetStatus.DRAFT) {
                throw new BadRequestException("Cannot modify a budget that is not DRAFT");
            }
            existing.setSalaryBudget(budget.getSalaryBudget());
            existing.setHeadcountBudget(budget.getHeadcountBudget());
            existing.setBenefitsBudget(budget.getBenefitsBudget());
            existing.setTrainingBudget(budget.getTrainingBudget());
            existing.setRecruitmentBudget(budget.getRecruitmentBudget());
            existing.setOvertimeBudget(budget.getOvertimeBudget());
            DepartmentBudget saved = repo.save(existing);
            audit.record(MODULE, ENTITY, saved.getId().toString(), "UPDATE", null, null);
            return saved;
        } else {
            // Create new
            budget.setTenantId(TENANT);
            budget.setCycleId(cycleId);
            budget.setCreatedBy(currentRequest.username());
            DepartmentBudget saved = repo.save(budget);
            audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, null);
            return saved;
        }
    }

    /**
     * Submit for approval → start BUDGET_APPROVAL workflow.
     */
    @Transactional
    public DepartmentBudget submit(UUID id) {
        DepartmentBudget budget = get(id);
        if (budget.getStatus() != DepartmentBudgetStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT budgets can be submitted");
        }

        BudgetCycle cycle = cycles.findById(budget.getCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("Budget cycle not found"));
        if (cycle.getStatus() != BudgetCycleStatus.OPEN) {
            throw new BadRequestException("Cannot submit budgets for a non-OPEN cycle");
        }

        // Start workflow
        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                id.toString(),
                "Department budget: " + budget.getOrgUnitId() + " (" + cycle.getCode() + ")",
                Map.of(
                        "cycleId", budget.getCycleId().toString(),
                        "orgUnitId", budget.getOrgUnitId().toString(),
                        "totalBudget", budget.getTotalBudget().toPlainString()
                )
        ));

        budget.setWorkflowInstanceId(instance.getId());
        budget.setStatus(DepartmentBudgetStatus.SUBMITTED);
        DepartmentBudget saved = repo.save(budget);

        audit.record(MODULE, ENTITY, id.toString(), "SUBMITTED", null,
                Map.of("workflowInstanceId", instance.getId().toString()));

        return saved;
    }

    /**
     * Workflow completion listener (AFTER_COMMIT, REQUIRES_NEW).
     * APPROVED → mark approved; REJECTED → mark rejected.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!ENTITY.equals(event.subjectEntity())) return;

        UUID budgetId = UUID.fromString(event.subjectId());
        requiresNew.executeWithoutResult(status -> {
            DepartmentBudget budget = repo.findById(budgetId).orElse(null);
            if (budget == null) {
                log.warn("Department budget not found for workflow completion: {}", budgetId);
                return;
            }

            // Idempotent: if already terminal, do nothing
            if (budget.getStatus() == DepartmentBudgetStatus.APPROVED
                    || budget.getStatus() == DepartmentBudgetStatus.REJECTED) {
                return;
            }

            switch (event.status()) {
                case APPROVED, AUTO_APPROVED -> {
                    budget.setStatus(DepartmentBudgetStatus.APPROVED);
                    budget.setApprovedBy(currentRequest.username());
                    budget.setApprovedAt(OffsetDateTime.now());
                    repo.save(budget);
                    audit.record(MODULE, ENTITY, budgetId.toString(), "APPROVED", null, null);
                }
                case REJECTED -> {
                    budget.setStatus(DepartmentBudgetStatus.REJECTED);
                    repo.save(budget);
                    audit.record(MODULE, ENTITY, budgetId.toString(), "REJECTED", null, null);
                }
                default -> {
                    log.warn("Unexpected workflow status for department budget {}: {}", budgetId, event.status());
                }
            }
        });
    }
}
