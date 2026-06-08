package az.millers.hcm.workflow.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.security.scope.WorkflowSubjectResolver;
import az.millers.hcm.workflow.api.dto.ActionRequest;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.ActionType;
import az.millers.hcm.workflow.domain.WorkflowAction;
import az.millers.hcm.workflow.domain.WorkflowDefinition;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.domain.WorkflowStep;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.repo.WorkflowActionRepository;
import az.millers.hcm.workflow.repo.WorkflowDefinitionRepository;
import az.millers.hcm.workflow.repo.WorkflowInstanceRepository;
import az.millers.hcm.workflow.repo.WorkflowStepRepository;

/**
 * Workflow Engine foundation (PRD Section 9).
 *
 * <p>This iteration supports <b>sequential</b> approvals only. Parallel,
 * conditional, delegation, SLA-based escalation, and substitute approvers are
 * deliberate later-work seams — the data model already accommodates them.
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowStepRepository steps;
    private final WorkflowInstanceRepository instances;
    private final WorkflowActionRepository actions;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;
    private final WorkflowSubjectResolver subjectResolver;
    private final EmployeeRepository employees;
    private final OrgUnitRepository orgUnits;

    public WorkflowService(WorkflowDefinitionRepository definitions,
                           WorkflowStepRepository steps,
                           WorkflowInstanceRepository instances,
                           WorkflowActionRepository actions,
                           ApplicationEventPublisher events,
                           ObjectMapper objectMapper,
                           CurrentRequest currentRequest,
                           AccessScopeService accessScope,
                           WorkflowSubjectResolver subjectResolver,
                           EmployeeRepository employees,
                           OrgUnitRepository orgUnits) {
        this.definitions = definitions;
        this.steps = steps;
        this.instances = instances;
        this.actions = actions;
        this.events = events;
        this.objectMapper = objectMapper;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
        this.subjectResolver = subjectResolver;
        this.employees = employees;
        this.orgUnits = orgUnits;
    }

    // ---------- Definitions ----------

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> listDefinitions() {
        return definitions.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public WorkflowDefinition getDefinition(String code) {
        return definitions.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + code));
    }

    @Transactional(readOnly = true)
    public List<WorkflowStep> stepsFor(UUID definitionId) {
        return steps.findByDefinitionIdOrderByStepOrderAsc(definitionId);
    }

    // ---------- Instance queries ----------

    @Transactional(readOnly = true)
    public WorkflowInstance get(UUID id) {
        WorkflowInstance i = instances.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow instance not found: " + id));
        // ABAC: a scoped caller (DEPARTMENT_MANAGER, org-unit-scoped
        // HR_SPECIALIST, EMPLOYEE) only sees workflow instances whose
        // employee-owned subject is in their scope. Org-wide subjects
        // (OrgVersion, PayrollRun) have no resolver and pass through —
        // those rely on the @PreAuthorize role gate.
        //
        // 404 (not 403) keeps consistency with the rest of the ABAC story
        // (M22/M24/M26) — an enumeration attempt can't confirm row
        // existence. Acts as the single guard for both
        // GET /api/workflow/instances/{id} and the action POST, which
        // calls this method as its first step (PRD 14.9).
        if (!accessScope.isWorkflowSubjectAccessible(i.getSubjectEntity(), i.getSubjectId())) {
            throw new ResourceNotFoundException("Workflow instance not found: " + id);
        }
        return i;
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstance> findForSubject(String module, String entity, String id) {
        return instances.findBySubjectModuleAndSubjectEntityAndSubjectIdOrderByInitiatedAtDesc(
                module, entity, id);
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstance> inboxFor(List<String> roles) {
        if (roles.isEmpty()) return List.of();
        List<WorkflowInstance> candidates = instances.findByStatusAndCurrentStepRoleInOrderByInitiatedAtDesc(
                WorkflowStatus.PENDING, roles);

        // M35: for manager-resolved steps, ONLY the subject's direct
        // manager sees the row in their inbox — not every user with
        // ROLE_DEPARTMENT_MANAGER in the org. SYSTEM_ADMIN keeps its
        // bypass (unrestricted callers skip the filter entirely below).
        // The resolver runs even for unrestricted callers because the
        // approver-narrowing is a semantic filter, not an ABAC one —
        // an HR_ADMIN looking at the inbox doesn't want every team's
        // pending leave to show up under their name either.
        UUID myEmpId = currentEmployeeIdOrNull();
        boolean isSystemAdmin = roles.contains("ROLE_SYSTEM_ADMIN");

        return candidates.stream()
                .filter(i -> {
                    // ABAC layer (M24/M26/M30): scope-bound subjects only.
                    if (!accessScope.isUnrestricted()
                            && !accessScope.isWorkflowSubjectAccessible(
                                    i.getSubjectEntity(), i.getSubjectId())) {
                        return false;
                    }
                    // M35 layer: manager-resolved step narrows to the
                    // subject's actual manager. SYSTEM_ADMIN bypasses.
                    if (isSystemAdmin) return true;
                    return matchesResolvedApprover(i, myEmpId);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstance> initiatedBy(String username) {
        return instances.findByInitiatedByOrderByInitiatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public List<WorkflowAction> history(UUID instanceId) {
        // Pull the instance through get(id) so the same ABAC visibility
        // check applies — a scoped caller who can't see the instance
        // can't see its action history either (PRD 14.9).
        get(instanceId);
        return actions.findByInstanceIdOrderByCreatedAtAsc(instanceId);
    }

    // ---------- Lifecycle ----------

    @Transactional
    public WorkflowInstance start(StartWorkflowRequest req) {
        WorkflowDefinition def = getDefinition(req.definitionCode());
        if (!def.isActive()) {
            throw new BadRequestException("Workflow definition is inactive: " + def.getCode());
        }
        List<WorkflowStep> defSteps = stepsFor(def.getId());

        WorkflowInstance i = new WorkflowInstance();
        i.setDefinitionId(def.getId());
        i.setDefinitionCode(def.getCode());
        i.setSubjectModule(req.subjectModule());
        i.setSubjectEntity(req.subjectEntity());
        i.setSubjectId(req.subjectId());
        i.setTitle(req.title());
        i.setInitiatedBy(currentRequest.username());
        i.setPayload(toJson(req.payload()));

        if (def.isAutoApprove() || defSteps.isEmpty()) {
            i.setCurrentStepIndex(0);
            i.setCurrentStepRole(null);
            i.setStatus(WorkflowStatus.AUTO_APPROVED);
            i.setCompletedAt(OffsetDateTime.now());
            WorkflowInstance saved = instances.save(i);
            recordAction(saved, 0, null, ActionType.AUTO_APPROVE, "Auto-approved (no approval steps).");
            publishCompleted(saved, "Auto-approved (no approval steps).");
            return saved;
        }

        WorkflowStep first = defSteps.get(0);
        i.setCurrentStepIndex(first.getStepOrder());
        i.setCurrentStepRole(first.getApproverRole());
        i.setCurrentStepEnteredAt(OffsetDateTime.now()); // M126 — SLA timer starts now
        i.setStatus(WorkflowStatus.PENDING);
        WorkflowInstance saved = instances.save(i);
        recordAction(saved, first.getStepOrder(), first.getName(), ActionType.START, null);
        return saved;
    }

    @Transactional
    public WorkflowInstance act(UUID instanceId, ActionRequest req) {
        WorkflowInstance i = get(instanceId);
        if (i.getStatus().isTerminal()) {
            throw new BadRequestException("Workflow is already " + i.getStatus());
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BadRequestException("Not authenticated");
        }

        String actor = authentication.getName();
        List<String> myRoles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        boolean isInitiator = actor.equals(i.getInitiatedBy());
        boolean isAdmin = myRoles.contains("ROLE_SYSTEM_ADMIN");

        List<WorkflowStep> defSteps = stepsFor(i.getDefinitionId());
        WorkflowStep currentStep = defSteps.stream()
                .filter(s -> s.getStepOrder() == i.getCurrentStepIndex())
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Current step " + i.getCurrentStepIndex() + " missing from definition"));

        switch (req.action()) {
            case CANCEL -> {
                require(isInitiator || isAdmin, "Only the initiator or a system admin can cancel");
                i.setStatus(WorkflowStatus.CANCELLED);
                i.setCompletedAt(OffsetDateTime.now());
                i.setCurrentStepRole(null);
                WorkflowInstance saved = instances.save(i);
                recordAction(saved, currentStep.getStepOrder(), currentStep.getName(),
                        ActionType.CANCEL, req.comment());
                publishCompleted(saved, req.comment());
                return saved;
            }
            case COMMENT -> {
                if (!StringUtils.hasText(req.comment())) {
                    throw new BadRequestException("Comment text is required");
                }
                recordAction(i, currentStep.getStepOrder(), currentStep.getName(),
                        ActionType.COMMENT, req.comment());
                return i;
            }
            case APPROVE -> {
                requireRole(myRoles, currentStep.getApproverRole(),
                        "You don't have the role required to approve this step ("
                                + currentStep.getApproverRole() + ")");
                requireResolvedApprover(i, currentStep, isAdmin,
                        "Only the subject's direct manager can approve this step");
                require(!isInitiator, "The initiator cannot approve their own request (segregation of duties)");
                recordAction(i, currentStep.getStepOrder(), currentStep.getName(),
                        ActionType.APPROVE, req.comment());
                WorkflowStep next = nextStep(defSteps, currentStep.getStepOrder());
                if (next == null) {
                    i.setStatus(WorkflowStatus.APPROVED);
                    i.setCompletedAt(OffsetDateTime.now());
                    i.setCurrentStepRole(null);
                    WorkflowInstance saved = instances.save(i);
                    publishCompleted(saved, req.comment());
                    return saved;
                }
                i.setCurrentStepIndex(next.getStepOrder());
                i.setCurrentStepRole(next.getApproverRole());
                i.setCurrentStepEnteredAt(OffsetDateTime.now()); // M126 — restart SLA timer for the new step
                return instances.save(i);
            }
            case REJECT -> {
                requireRole(myRoles, currentStep.getApproverRole(),
                        "You don't have the role required to reject this step");
                requireResolvedApprover(i, currentStep, isAdmin,
                        "Only the subject's direct manager can reject this step");
                if (!StringUtils.hasText(req.comment())) {
                    throw new BadRequestException("A reason is required to reject");
                }
                i.setStatus(WorkflowStatus.REJECTED);
                i.setCompletedAt(OffsetDateTime.now());
                i.setCurrentStepRole(null);
                WorkflowInstance saved = instances.save(i);
                recordAction(saved, currentStep.getStepOrder(), currentStep.getName(),
                        ActionType.REJECT, req.comment());
                publishCompleted(saved, req.comment());
                return saved;
            }
            case RETURN -> {
                requireRole(myRoles, currentStep.getApproverRole(),
                        "You don't have the role required to return this step");
                requireResolvedApprover(i, currentStep, isAdmin,
                        "Only the subject's direct manager can return this step");
                if (!StringUtils.hasText(req.comment())) {
                    throw new BadRequestException("A reason is required to return");
                }
                i.setStatus(WorkflowStatus.RETURNED);
                i.setCompletedAt(OffsetDateTime.now());
                i.setCurrentStepRole(null);
                WorkflowInstance saved = instances.save(i);
                recordAction(saved, currentStep.getStepOrder(), currentStep.getName(),
                        ActionType.RETURN, req.comment());
                publishCompleted(saved, req.comment());
                return saved;
            }
            default -> throw new BadRequestException(
                    "Action " + req.action() + " cannot be invoked directly");
        }
    }

    // ---------- Internals ----------

    private WorkflowStep nextStep(List<WorkflowStep> all, int currentOrder) {
        return all.stream()
                .filter(s -> s.getStepOrder() > currentOrder)
                .findFirst()
                .orElse(null);
    }

    private void recordAction(WorkflowInstance i, int stepIndex, String stepName,
                              ActionType type, String comment) {
        WorkflowAction a = new WorkflowAction();
        a.setInstanceId(i.getId());
        a.setStepIndex(stepIndex);
        a.setStepName(stepName);
        a.setAction(type);
        a.setActor(currentRequest.username());
        a.setComment(comment);
        a.setIpAddress(currentRequest.ipAddress());
        actions.save(a);
    }

    private void publishCompleted(WorkflowInstance i, String comment) {
        events.publishEvent(new WorkflowCompletedEvent(
                i.getId(), i.getDefinitionCode(), i.getSubjectModule(),
                i.getSubjectEntity(), i.getSubjectId(), i.getStatus(), comment,
                currentRequest.username()));
    }

    private String toJson(Object payload) {
        if (payload == null) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"_serializationError\":\"" + ex.getOriginalMessage() + "\"}";
        }
    }

    private void requireRole(List<String> myRoles, String required, String message) {
        if (myRoles.contains("ROLE_SYSTEM_ADMIN")) return;
        if (!myRoles.contains(required)) {
            throw new BadRequestException(message);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new BadRequestException(message);
    }

    // ---------- M35: real MANAGER step resolution ----------

    /**
     * Resolves the caller (`currentRequest.username()`) to an
     * {@link Employee} id, or {@code null} if no mapping exists. Used
     * by the manager-step gate. Mirrors the EmployeeContextService
     * lookup pattern but stays internal so we don't pull the whole
     * self-service module in.
     */
    private UUID currentEmployeeIdOrNull() {
        String username = currentRequest.username();
        if (username == null) return null;
        return employees.findByUsername(username).map(Employee::getId).orElse(null);
    }

    /**
     * For a workflow instance whose current step {@code resolvesToManager},
     * returns the id of the employee who should act on the step today.
     *
     * <p>Two-step resolution:
     * <ol>
     *   <li>Subject → {@code manager_id} (existing M35 lookup).</li>
     *   <li>If the manager has an <em>active</em> delegation
     *       ({@code delegate_manager_id} set + today() ∈
     *       {@code [delegate_from, delegate_to]}), return the delegate's
     *       id instead. Single hop only — no chain walk, so a delegate
     *       who is themselves on leave doesn't recursively re-delegate.
     *       That's a deliberate constraint: cycles are impossible by
     *       construction (the SQL check refuses self-delegation), but
     *       multi-hop fan-out makes the routing harder to reason about
     *       than HR wants in practice (PRD 9 / 14.9 — M37).</li>
     * </ol>
     *
     * <p>Returns {@code null} when the subject can't be resolved, isn't
     * employee-scoped, or the manager has no {@code manager_id}
     * (top-of-org).
     */
    private UUID resolveSubjectManagerId(WorkflowInstance i) {
        Optional<UUID> subjectMgr = subjectResolver
                .resolveEmployeeId(i.getSubjectEntity(), i.getSubjectId())
                .flatMap(employees::findById)
                .map(Employee::getManagerId);
        if (subjectMgr.isEmpty()) return null;

        return employees.findById(subjectMgr.get())
                .map(this::effectiveApprover)
                .orElse(subjectMgr.get());
    }

    /**
     * Single-hop delegation walk. Returns the delegate's id when
     * delegation is set AND today is within the window; otherwise
     * returns {@code manager.id} unchanged.
     */
    private UUID effectiveApprover(Employee manager) {
        UUID delegate = manager.getDelegateManagerId();
        if (delegate == null) return manager.getId();
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate from = manager.getDelegateFrom();
        java.time.LocalDate to   = manager.getDelegateTo();
        if (from == null || to == null) return manager.getId();
        if (today.isBefore(from) || today.isAfter(to)) return manager.getId();
        // Active delegation — log at INFO so an operator reading the
        // backend log can correlate "why did Sara just see this row"
        // with HR's earlier delegation decision.
        log.info("Workflow approval routed to delegate {} (manager {}, window {}..{})",
                delegate, manager.getId(), from, to);
        return delegate;
    }

    /**
     * M142 — resolves the HRBP employee id for a workflow instance.
     *
     * <p>Resolution chain:
     * <ol>
     *   <li>Subject → employee id (via {@link WorkflowSubjectResolver}).</li>
     *   <li>Employee → {@code org_unit_id}.</li>
     *   <li>Walk up the org tree (max 20 hops) looking for the first unit
     *       with {@code hrbp_id} set.</li>
     * </ol>
     *
     * <p>Returns {@code null} when the subject can't be resolved, has no
     * org unit, or no ancestor carries an HRBP assignment.
     */
    private UUID resolveSubjectHrbpId(WorkflowInstance i) {
        Optional<UUID> subjectEmpId = subjectResolver
                .resolveEmployeeId(i.getSubjectEntity(), i.getSubjectId());
        if (subjectEmpId.isEmpty()) return null;
        Optional<Employee> emp = employees.findById(subjectEmpId.get());
        if (emp.isEmpty() || emp.get().getOrgUnitId() == null) return null;
        UUID unitId = emp.get().getOrgUnitId();
        int depth = 0;
        while (unitId != null && depth < 20) {
            Optional<OrgUnit> unit = orgUnits.findById(unitId);
            if (unit.isEmpty()) break;
            if (unit.get().getHrbpId() != null) return unit.get().getHrbpId();
            unitId = unit.get().getParentId();
            depth++;
        }
        return null;
    }

    /**
     * Inbox filter for manager- or HRBP-resolved steps. Returns true iff
     * the row should show up for the caller. Behaviour:
     * <ul>
     *   <li>Step doesn't resolve to manager or HRBP → keep (role gate is
     *       enough).</li>
     *   <li>Caller isn't mapped to an Employee → drop.</li>
     *   <li>Subject has no manager/HRBP → drop.</li>
     *   <li>Otherwise → keep only if caller's empId equals the resolved id.</li>
     * </ul>
     */
    private boolean matchesResolvedApprover(WorkflowInstance i, UUID callerEmpId) {
        WorkflowStep step = currentStepFor(i);
        if (step == null) return true;
        if (step.isResolvesToManager()) {
            if (callerEmpId == null) return false;
            UUID expectedMgr = resolveSubjectManagerId(i);
            return expectedMgr != null && expectedMgr.equals(callerEmpId);
        }
        if (step.isResolvesToHrbp()) {
            if (callerEmpId == null) return false;
            UUID expectedHrbp = resolveSubjectHrbpId(i);
            return expectedHrbp != null && expectedHrbp.equals(callerEmpId);
        }
        return true;
    }

    /**
     * Throws on a manager- or HRBP-resolved step when the caller isn't the
     * expected approver. SYSTEM_ADMIN ({@code isAdmin}) bypasses.
     */
    private void requireResolvedApprover(WorkflowInstance i, WorkflowStep step,
                                          boolean isAdmin, String message) {
        if (isAdmin) return;
        if (step.isResolvesToManager()) {
            UUID expectedMgr = resolveSubjectManagerId(i);
            if (expectedMgr == null) {
                throw new BadRequestException(
                        "This step requires a direct manager but the subject has none assigned");
            }
            if (!expectedMgr.equals(currentEmployeeIdOrNull())) {
                throw new BadRequestException(message);
            }
        } else if (step.isResolvesToHrbp()) {
            UUID expectedHrbp = resolveSubjectHrbpId(i);
            if (expectedHrbp == null) {
                throw new BadRequestException(
                        "This step requires an HRBP but none is assigned to the subject's org unit");
            }
            if (!expectedHrbp.equals(currentEmployeeIdOrNull())) {
                throw new BadRequestException(message);
            }
        }
    }

    /**
     * Looks up the current step on an instance. Returns {@code null}
     * if the step row is missing (defensive — should never happen on a
     * live PENDING instance, but the inbox filter shouldn't crash on
     * mis-seeded definitions).
     */
    private WorkflowStep currentStepFor(WorkflowInstance i) {
        return stepsFor(i.getDefinitionId()).stream()
                .filter(s -> s.getStepOrder() == i.getCurrentStepIndex())
                .findFirst()
                .orElse(null);
    }
}
