package az.millers.hcm.corehr.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.PersonalInfoChangeResponse;
import az.millers.hcm.corehr.api.dto.PersonalInfoChangeSubmitRequest;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.PersonalInfoChangeRequest;
import az.millers.hcm.corehr.domain.PersonalInfoChangeStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.repo.PersonalInfoChangeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;
import az.millers.hcm.common.BusinessNumbers;

/**
 * Personal-info change-request service (M79 / P2-25/26).
 *
 * <p>State machine:
 * <pre>
 *   submit() — PENDING + workflow started
 *   onApproved() — workflow callback → APPROVED then apply → APPLIED
 *   onRejected() — workflow callback → REJECTED
 *   onCancelled() — workflow callback → CANCELLED
 * </pre>
 */
@Service
public class PersonalInfoChangeService {

    public static final String WORKFLOW_DEFINITION = "PERSONAL_INFO_CHANGE_APPROVAL";
    public static final String WORKFLOW_ENTITY = "PersonalInfoChangeRequest";
    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "PersonalInfoChangeRequest";

    private final PersonalInfoChangeRepository repository;
    private final EmployeeRepository employees;
    private final PersonalInfoFieldValidator validator;
    private final WorkflowService workflows;
    private final AuditService audit;
    private final AccessScopeService scope;
    private final CurrentRequest currentRequest;

    public PersonalInfoChangeService(PersonalInfoChangeRepository repository,
                                      EmployeeRepository employees,
                                      PersonalInfoFieldValidator validator,
                                      WorkflowService workflows,
                                      AuditService audit,
                                      AccessScopeService scope,
                                      CurrentRequest currentRequest) {
        this.repository = repository;
        this.employees = employees;
        this.validator = validator;
        this.workflows = workflows;
        this.audit = audit;
        this.scope = scope;
        this.currentRequest = currentRequest;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PersonalInfoChangeRequest> list(PersonalInfoChangeStatus status, Pageable pageable) {
        var s = scope.scopeForCurrentUser();
        if (s.isEmpty()) {
            return status == null
                    ? repository.findAllByOrderBySubmittedAtDesc(pageable)
                    : repository.findByStatusOrderBySubmittedAtDesc(status, pageable);
        }
        var ids = s.get();
        if (ids.isEmpty()) return Page.empty(pageable);
        return status == null
                ? repository.findByEmployeeIdInOrderBySubmittedAtDesc(ids, pageable)
                : repository.findByEmployeeIdInAndStatusOrderBySubmittedAtDesc(ids, status, pageable);
    }

    @Transactional(readOnly = true)
    public java.util.List<PersonalInfoChangeRequest> mine(UUID employeeId) {
        return repository.findByEmployeeIdOrderBySubmittedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public PersonalInfoChangeRequest get(UUID id) {
        PersonalInfoChangeRequest r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PersonalInfoChangeRequest not found: " + id));
        if (!scope.isAccessible(r.getEmployeeId())) {
            throw new ResourceNotFoundException(
                    "PersonalInfoChangeRequest not found: " + id);
        }
        return r;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Transactional
    public PersonalInfoChangeRequest submit(PersonalInfoChangeSubmitRequest req) {
        Employee employee = employees.findById(req.employeeId())
                .orElseThrow(() -> new BadRequestException(
                        "Employee not found: " + req.employeeId()));

        // Whitelist + format check before persisting anything.
        validator.validate(req.fieldKey(), req.newValue());

        String oldValue = validator.currentValue(employee, req.fieldKey());
        if (Objects.equals(oldValue, normalise(req.newValue()))) {
            throw new BadRequestException(
                    "New value is identical to the current value — nothing to approve");
        }

        PersonalInfoChangeRequest r = new PersonalInfoChangeRequest();
        r.setRequestNo(BusinessNumbers.format("PIC", 6, repository.nextRequestNoSequence()));
        r.setEmployeeId(employee.getId());
        r.setFieldKey(req.fieldKey());
        r.setOldValue(oldValue);
        r.setNewValue(normalise(req.newValue()));
        r.setReason(req.reason());
        r.setSubmittedBy(currentRequest.username());
        r.setCreatedBy(currentRequest.username());
        r.setUpdatedBy(currentRequest.username());
        PersonalInfoChangeRequest saved = repository.save(r);

        WorkflowInstance wf = workflows.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                WORKFLOW_ENTITY,
                saved.getId().toString(),
                "Personal info change " + saved.getRequestNo()
                        + " (" + saved.getFieldKey() + ")",
                Map.of(
                        "employeeId", employee.getId().toString(),
                        "fieldKey", saved.getFieldKey())));
        saved.setWorkflowInstanceId(wf.getId());
        saved = repository.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, PersonalInfoChangeResponse.from(saved));
        return saved;
    }

    @Transactional
    public PersonalInfoChangeRequest onApproved(UUID id, String approver, String comment) {
        PersonalInfoChangeRequest r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Change request not found: " + id));
        if (r.getStatus() != PersonalInfoChangeStatus.PENDING) return r;
        PersonalInfoChangeResponse before = PersonalInfoChangeResponse.from(r);

        // Apply immediately on approval — re-validate so a value that snuck
        // through stale rules at submit time still fails closed.
        Employee employee = employees.findById(r.getEmployeeId())
                .orElseThrow(() -> new IllegalStateException(
                        "Employee vanished mid-flow: " + r.getEmployeeId()));
        boolean mutated = validator.apply(employee, r.getFieldKey(), r.getNewValue());
        if (mutated) {
            employee.setUpdatedBy(approver == null ? "system" : approver);
            employees.save(employee);
        }

        r.setStatus(PersonalInfoChangeStatus.APPLIED);
        r.setDecidedAt(OffsetDateTime.now());
        r.setAppliedAt(OffsetDateTime.now());
        r.setDecidedBy(approver);
        r.setDecisionComment(comment);
        r.setUpdatedBy(approver == null ? currentRequest.username() : approver);
        PersonalInfoChangeRequest saved = repository.save(r);

        audit.record(MODULE, ENTITY, id.toString(),
                "APPROVE_AND_APPLY", before, PersonalInfoChangeResponse.from(saved));
        return saved;
    }

    @Transactional
    public PersonalInfoChangeRequest onRejected(UUID id, String approver, String comment) {
        return transition(id, PersonalInfoChangeStatus.REJECTED, approver, comment, "REJECT");
    }

    @Transactional
    public PersonalInfoChangeRequest onCancelled(UUID id, String approver, String comment) {
        return transition(id, PersonalInfoChangeStatus.CANCELLED, approver, comment, "CANCEL");
    }

    private PersonalInfoChangeRequest transition(UUID id, PersonalInfoChangeStatus newStatus,
                                                  String actor, String comment, String auditAction) {
        PersonalInfoChangeRequest r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Change request not found: " + id));
        if (r.getStatus() == newStatus) return r;
        PersonalInfoChangeResponse before = PersonalInfoChangeResponse.from(r);
        r.setStatus(newStatus);
        r.setDecidedAt(OffsetDateTime.now());
        r.setDecidedBy(actor);
        r.setDecisionComment(comment);
        r.setUpdatedBy(actor == null ? currentRequest.username() : actor);
        PersonalInfoChangeRequest saved = repository.save(r);
        audit.record(MODULE, ENTITY, id.toString(), auditAction,
                before, PersonalInfoChangeResponse.from(saved));
        return saved;
    }

    private static String normalise(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
