package az.millers.hcm.lifecycle.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.api.dto.AppealRequest;
import az.millers.hcm.lifecycle.api.dto.DisciplinaryActionRequest;
import az.millers.hcm.lifecycle.api.dto.DisciplinaryActionResponse;
import az.millers.hcm.lifecycle.domain.DisciplinaryAction;
import az.millers.hcm.lifecycle.domain.DisciplinaryActionType;
import az.millers.hcm.lifecycle.domain.DisciplinaryStatus;
import az.millers.hcm.lifecycle.repo.DisciplinaryActionRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * Manages the {@link DisciplinaryAction} lifecycle (M67 / P1-12).
 *
 * <p>State machine implemented exactly as documented on {@link DisciplinaryStatus}.
 * Routes through the existing {@code WorkflowService.start()} pattern — same
 * shape as {@link ContractChangeService} and {@link TerminationService}, so the
 * approval inbox / scope-gating / completion event listener all work without
 * any framework change. The {@link LifecycleWorkflowListener} dispatches the
 * terminal {@code WorkflowCompletedEvent} back to {@code onApproved()} /
 * {@code onRejected()} / {@code onCancelled()} below.
 */
@Service
public class DisciplinaryActionService {

    public static final String WORKFLOW_DEFINITION = "DISCIPLINARY_ACTION_APPROVAL";
    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "DisciplinaryAction";

    private final DisciplinaryActionRepository repository;
    private final EmployeeRepository employees;
    private final WorkflowService workflowService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public DisciplinaryActionService(DisciplinaryActionRepository repository,
                                      EmployeeRepository employees,
                                      WorkflowService workflowService,
                                      NotificationService notifications,
                                      AuditService audit,
                                      AccessScopeService accessScope,
                                      CurrentRequest currentRequest) {
        this.repository = repository;
        this.employees = employees;
        this.workflowService = workflowService;
        this.notifications = notifications;
        this.audit = audit;
        this.accessScope = accessScope;
        this.currentRequest = currentRequest;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DisciplinaryActionResponse get(UUID id) {
        DisciplinaryAction d = loadOrThrow(id);
        ensureEmployeeAccessible(d.getEmployeeId());
        return DisciplinaryActionResponse.from(d);
    }

    @Transactional(readOnly = true)
    public Page<DisciplinaryActionResponse> list(DisciplinaryStatus status, Pageable pageable) {
        Page<DisciplinaryAction> page = status != null
                ? repository.findByStatusOrderByActionDateDesc(status, pageable)
                : repository.findAllByOrderByActionDateDesc(pageable);
        return page.map(DisciplinaryActionResponse::from);
    }

    @Transactional(readOnly = true)
    public List<DisciplinaryActionResponse> listForEmployee(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdOrderByActionDateDesc(employeeId)
                .stream().map(DisciplinaryActionResponse::from).toList();
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Creates a DRAFT action — visible only to HR, not yet routed for approval.
     */
    @Transactional
    public DisciplinaryActionResponse create(DisciplinaryActionRequest req) {
        Employee emp = employees.findById(req.employeeId()).orElseThrow(() ->
                new BadRequestException("Employee not found: " + req.employeeId()));
        validateDates(req);

        DisciplinaryAction d = new DisciplinaryAction();
        d.setActionNo(nextActionNo());
        d.setEmployeeId(emp.getId());
        d.setActionType(req.actionType());
        d.setIncidentDate(req.incidentDate());
        d.setActionDate(req.actionDate());
        d.setDescription(req.description());
        d.setLinkedCaseId(req.linkedCaseId());
        d.setStatus(DisciplinaryStatus.DRAFT);
        d.setCreatedBy(currentRequest.username());
        d.setUpdatedBy(currentRequest.username());
        DisciplinaryAction saved = repository.save(d);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, DisciplinaryActionResponse.from(saved));
        return DisciplinaryActionResponse.from(saved);
    }

    /**
     * Submit a DRAFT action for approval. Starts the workflow and flips status
     * to PENDING. The completion event later routes back through
     * {@code LifecycleWorkflowListener → onApproved/onRejected}.
     */
    @Transactional
    public DisciplinaryActionResponse submit(UUID id) {
        DisciplinaryAction d = loadOrThrow(id);
        if (d.getStatus() != DisciplinaryStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT actions can be submitted for approval");
        }
        Employee emp = employees.findById(d.getEmployeeId()).orElseThrow(() ->
                new BadRequestException("Employee not found: " + d.getEmployeeId()));

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                d.getId().toString(),
                d.getActionNo() + " — " + d.getActionType() + " for "
                        + emp.getFirstName() + " " + emp.getLastName(),
                Map.of(
                        "actionNo", d.getActionNo(),
                        "employeeNo", emp.getEmployeeNo(),
                        "actionType", d.getActionType().name(),
                        "incidentDate", d.getIncidentDate().toString(),
                        "isDismissal", d.getActionType().isDismissalLevel())));
        d.setWorkflowInstanceId(instance.getId());
        d.setStatus(DisciplinaryStatus.PENDING);
        d.setUpdatedBy(currentRequest.username());
        DisciplinaryAction saved = repository.save(d);

        audit.record(MODULE, ENTITY, id.toString(), "SUBMIT", null,
                Map.of("workflowInstanceId", instance.getId().toString()));
        return DisciplinaryActionResponse.from(saved);
    }

    /**
     * Workflow approved this action. Status → APPROVED, awaiting HR to issue.
     */
    @Transactional
    public DisciplinaryAction onApproved(UUID id, String comment) {
        DisciplinaryAction d = loadOrThrow(id);
        if (d.getStatus() != DisciplinaryStatus.PENDING) return d;
        d.setStatus(DisciplinaryStatus.APPROVED);
        d.setUpdatedBy(currentRequest.username());
        DisciplinaryAction saved = repository.save(d);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVED",
                null, Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public DisciplinaryAction onRejected(UUID id, String comment) {
        DisciplinaryAction d = loadOrThrow(id);
        if (d.getStatus() != DisciplinaryStatus.PENDING) return d;
        d.setStatus(DisciplinaryStatus.REJECTED);
        d.setUpdatedBy(currentRequest.username());
        DisciplinaryAction saved = repository.save(d);
        audit.record(MODULE, ENTITY, id.toString(), "REJECTED",
                null, Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public DisciplinaryAction onCancelled(UUID id, String comment) {
        DisciplinaryAction d = loadOrThrow(id);
        if (d.getStatus() != DisciplinaryStatus.PENDING) return d;
        d.setStatus(DisciplinaryStatus.REJECTED);
        d.setUpdatedBy(currentRequest.username());
        DisciplinaryAction saved = repository.save(d);
        audit.record(MODULE, ENTITY, id.toString(), "CANCELLED",
                null, Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    /**
     * Issue the approved action to the employee — sends a notification through
     * the existing {@code NotificationService} channels (in-app + email + push)
     * and stamps the issued_at timestamp.
     */
    @Transactional
    public DisciplinaryActionResponse issue(UUID id) {
        DisciplinaryAction d = loadOrThrow(id);
        if (d.getStatus() != DisciplinaryStatus.APPROVED) {
            throw new BadRequestException(
                    "Only APPROVED actions can be issued — current status: " + d.getStatus());
        }
        DisciplinaryActionResponse before = DisciplinaryActionResponse.from(d);

        d.setStatus(DisciplinaryStatus.ISSUED);
        d.setIssuedAt(OffsetDateTime.now());
        d.setIssuedBy(currentRequest.username());
        d.setUpdatedBy(currentRequest.username());
        DisciplinaryAction saved = repository.save(d);

        // Notify the employee — disciplinary delivery is a serious event that
        // employees are legally entitled to be informed of. Manager fan-out is
        // intentionally omitted: only the affected employee (and HR via the
        // audit log) sees the action.
        employees.findById(saved.getEmployeeId()).ifPresent(emp -> {
            if (emp.getUsername() != null && !emp.getUsername().isBlank()) {
                notifications.notifyAll(
                        emp.getUsername(),
                        "Disciplinary action issued — " + saved.getActionNo(),
                        "A " + saved.getActionType() + " action has been issued ("
                                + saved.getActionNo() + "). You may file an appeal "
                                + "through HR if you wish to contest the decision.",
                        MODULE, ENTITY, saved.getId().toString());
            }
        });

        audit.record(MODULE, ENTITY, id.toString(), "ISSUED",
                before, DisciplinaryActionResponse.from(saved));
        return DisciplinaryActionResponse.from(saved);
    }

    @Transactional
    public DisciplinaryActionResponse appeal(UUID id, AppealRequest req) {
        DisciplinaryAction d = loadOrThrow(id);
        if (d.getStatus() != DisciplinaryStatus.ISSUED) {
            throw new BadRequestException(
                    "Only ISSUED actions can be appealed — current status: " + d.getStatus());
        }
        DisciplinaryActionResponse before = DisciplinaryActionResponse.from(d);

        d.setStatus(DisciplinaryStatus.APPEALED);
        d.setAppealFlag(true);
        d.setAppealReason(req.reason());
        d.setAppealedAt(OffsetDateTime.now());
        d.setUpdatedBy(currentRequest.username());
        DisciplinaryAction saved = repository.save(d);

        audit.record(MODULE, ENTITY, id.toString(), "APPEAL_FILED",
                before, Map.of("reason", req.reason()));
        return DisciplinaryActionResponse.from(saved);
    }

    /**
     * Close an issued or appealed action. Optionally records the appeal
     * outcome when closing from APPEALED.
     */
    @Transactional
    public DisciplinaryActionResponse close(UUID id, String appealOutcome) {
        DisciplinaryAction d = loadOrThrow(id);
        if (d.getStatus() != DisciplinaryStatus.ISSUED
                && d.getStatus() != DisciplinaryStatus.APPEALED) {
            throw new BadRequestException(
                    "Only ISSUED or APPEALED actions can be closed — current status: "
                            + d.getStatus());
        }
        DisciplinaryActionResponse before = DisciplinaryActionResponse.from(d);

        d.setStatus(DisciplinaryStatus.CLOSED);
        d.setClosedAt(OffsetDateTime.now());
        if (d.getStatus() == DisciplinaryStatus.APPEALED && appealOutcome != null) {
            d.setAppealOutcome(appealOutcome);
        }
        d.setUpdatedBy(currentRequest.username());
        DisciplinaryAction saved = repository.save(d);

        audit.record(MODULE, ENTITY, id.toString(), "CLOSED",
                before, DisciplinaryActionResponse.from(saved));
        return DisciplinaryActionResponse.from(saved);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private DisciplinaryAction loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Disciplinary action not found: " + id));
    }

    private void validateDates(DisciplinaryActionRequest req) {
        if (req.actionDate().isBefore(req.incidentDate())) {
            throw new BadRequestException("actionDate must be on or after incidentDate");
        }
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }

    private String nextActionNo() {
        return String.format("DA-%05d", repository.nextActionNoSequence());
    }

    /** Surface for callers that need a typed helper instead of magic strings. */
    public DisciplinaryActionType resolveType(String actionType) {
        try {
            return DisciplinaryActionType.valueOf(actionType);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown disciplinary action type: " + actionType);
        }
    }
}
