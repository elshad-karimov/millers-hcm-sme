package az.millers.hcm.lifecycle.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.domain.EmployeeMovementRequest;
import az.millers.hcm.lifecycle.domain.MovementRequestStatus;
import az.millers.hcm.lifecycle.domain.MovementType;
import az.millers.hcm.lifecycle.repo.EmployeeMovementRequestRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * M432 — Employee movement requests (TRANSFER/PROMOTION). Manager initiates
 * for own reports (AccessScopeService), or HR initiates for anyone. EMP_MOVEMENT
 * workflow (HR_ADMIN approval). APPROVED → HR manually executes the transfer
 * (no auto-execute). proposed_salary visible ONLY to HR in responses.
 */
@Service
public class EmployeeMovementService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeMovementService.class);

    public static final String WORKFLOW_DEFINITION = "EMP_MOVEMENT";
    private static final String MODULE = "lifecycle";
    private static final String ENTITY = "EmployeeMovementRequest";
    private static final String TENANT = "default";

    private final EmployeeMovementRequestRepository repo;
    private final EmployeeRepository employees;
    private final WorkflowService workflowService;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate requiresNew;
    private final NotificationService notifications;

    public EmployeeMovementService(EmployeeMovementRequestRepository repo,
                                    EmployeeRepository employees,
                                    WorkflowService workflowService,
                                    AccessScopeService accessScope,
                                    AuditService audit,
                                    CurrentRequest currentRequest,
                                    NamedParameterJdbcTemplate jdbc,
                                    PlatformTransactionManager txManager,
                                    NotificationService notifications) {
        this.repo = repo;
        this.employees = employees;
        this.workflowService = workflowService;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.jdbc = jdbc;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.notifications = notifications;
    }

    @Transactional
    public EmployeeMovementRequest create(UUID employeeId,
                                           MovementType movementType,
                                           UUID proposedPositionId,
                                           UUID proposedOrgUnitId,
                                           String proposedGrade,
                                           BigDecimal proposedSalary,
                                           LocalDate effectiveDate,
                                           String justification) {
        // Access check: manager can only request for own reports, or HR can request for anyone
        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        boolean isHR = currentRequest.hasRole("HR_ADMIN");
        if (!isHR) {
            // Manager check: employee must be accessible
            if (!accessScope.isAccessible(employeeId)) {
                throw new BadRequestException("You can only create movement requests for your direct reports");
            }
        }

        EmployeeMovementRequest req = new EmployeeMovementRequest();
        req.setTenantId(TENANT);
        req.setEmployeeId(employeeId);
        req.setMovementType(movementType);
        req.setProposedPositionId(proposedPositionId);
        req.setProposedOrgUnitId(proposedOrgUnitId);
        req.setProposedGrade(proposedGrade);
        req.setProposedSalary(proposedSalary);
        req.setEffectiveDate(effectiveDate);
        req.setJustification(justification);
        req.setStatus(MovementRequestStatus.DRAFT);
        req.setRequestedBy(currentRequest.username());
        req.setCreatedBy(currentRequest.username());
        req.setUpdatedBy(currentRequest.username());

        EmployeeMovementRequest saved = repo.save(req);

        // Generate request number
        String requestNo = String.format("MV-%05d", jdbc.queryForObject(
                "SELECT nextval('lifecycle.employee_movement_request_no_seq')",
                java.util.Map.of(), Long.class));
        saved.setRequestNo(requestNo);
        saved = repo.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, null);

        return saved;
    }

    @Transactional
    public EmployeeMovementRequest submit(UUID id) {
        EmployeeMovementRequest req = get(id);
        if (req.getStatus() != MovementRequestStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT requests can be submitted (current: " + req.getStatus() + ")");
        }

        // Start workflow
        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                id.toString(),
                "Employee Movement Request " + req.getRequestNo(),
                null));

        req.setWorkflowInstanceId(instance.getId());
        req.setStatus(MovementRequestStatus.SUBMITTED);
        req.setUpdatedBy(currentRequest.username());
        req.setUpdatedAt(OffsetDateTime.now());
        EmployeeMovementRequest saved = repo.save(req);

        audit.record(MODULE, ENTITY, id.toString(), "SUBMITTED", null,
                java.util.Map.of("workflowInstanceId", instance.getId().toString()));

        return saved;
    }

    @Transactional
    public EmployeeMovementRequest cancel(UUID id) {
        EmployeeMovementRequest req = get(id);
        if (req.getStatus() != MovementRequestStatus.DRAFT && req.getStatus() != MovementRequestStatus.SUBMITTED) {
            throw new BadRequestException("Cannot cancel request in status " + req.getStatus());
        }
        req.setStatus(MovementRequestStatus.CANCELLED);
        req.setUpdatedBy(currentRequest.username());
        req.setUpdatedAt(OffsetDateTime.now());
        EmployeeMovementRequest saved = repo.save(req);
        audit.record(MODULE, ENTITY, id.toString(), "CANCELLED", null, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EmployeeMovementRequest> list() {
        return repo.findByTenantIdOrderByCreatedAtDesc(TENANT);
    }

    @Transactional(readOnly = true)
    public EmployeeMovementRequest get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee movement request not found: " + id));
    }

    /**
     * Workflow completion listener: APPROVED → mark APPROVED + notify HR to execute;
     * REJECTED → mark REJECTED + notify requester.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!WORKFLOW_DEFINITION.equals(event.definitionCode())) {
            return;
        }
        if (event.status() == WorkflowStatus.APPROVED) {
            requiresNew.executeWithoutResult(status -> {
                try {
                    UUID reqId = UUID.fromString(event.subjectId());
                    EmployeeMovementRequest req = repo.findById(reqId).orElse(null);
                    if (req == null || req.getStatus() != MovementRequestStatus.SUBMITTED) {
                        return;
                    }
                    req.setStatus(MovementRequestStatus.APPROVED);
                    req.setUpdatedAt(OffsetDateTime.now());
                    repo.save(req);
                    audit.record(MODULE, ENTITY, reqId.toString(), "APPROVED", null,
                            "Workflow approved. HR should now execute the transfer/promotion manually.");
                    log.info("Movement request {} APPROVED. HR should execute manually.", req.getRequestNo());
                } catch (Exception e) {
                    log.error("Failed to mark movement request approved: {}", e.getMessage(), e);
                }
            });
        } else if (event.status() == WorkflowStatus.REJECTED) {
            requiresNew.executeWithoutResult(status -> {
                try {
                    UUID reqId = UUID.fromString(event.subjectId());
                    EmployeeMovementRequest req = repo.findById(reqId).orElse(null);
                    if (req == null || req.getStatus() != MovementRequestStatus.SUBMITTED) {
                        return;
                    }
                    req.setStatus(MovementRequestStatus.REJECTED);
                    req.setUpdatedAt(OffsetDateTime.now());
                    repo.save(req);
                    audit.record(MODULE, ENTITY, reqId.toString(), "REJECTED", null, null);

                    // Notify requester
                    if (req.getRequestedBy() != null) {
                        notifications.notifyAll(
                                req.getRequestedBy(),
                                "Movement Request Rejected",
                                "Your movement request " + req.getRequestNo() + " was rejected.",
                                MODULE,
                                ENTITY,
                                reqId.toString()
                        );
                    }
                } catch (Exception e) {
                    log.error("Failed to mark movement request rejected: {}", e.getMessage(), e);
                }
            });
        }
    }
}
