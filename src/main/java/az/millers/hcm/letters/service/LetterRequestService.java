package az.millers.hcm.letters.service;

import java.time.OffsetDateTime;
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
import az.millers.hcm.letters.api.dto.LetterRequestResponse;
import az.millers.hcm.letters.api.dto.LetterSubmitRequest;
import az.millers.hcm.letters.domain.LetterRequest;
import az.millers.hcm.letters.domain.LetterStatus;
import az.millers.hcm.letters.domain.LetterTemplate;
import az.millers.hcm.letters.repo.LetterRequestRepository;
import az.millers.hcm.letters.repo.LetterTemplateRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * Submit / approve / render letter requests (M77 / P2-17).
 *
 * <p>State machine:
 * <pre>
 *   submit() — DRAFT → PENDING (or directly to ISSUED for auto-approve templates)
 *   onApproved()  — workflow callback → APPROVED → ISSUED (renders body)
 *   onRejected()  — workflow callback → REJECTED
 *   onCancelled() — workflow callback → CANCELLED
 *   issue()       — explicit issue for auto-approve templates (called inline by submit)
 * </pre>
 */
@Service
public class LetterRequestService {

    public static final String WORKFLOW_DEFINITION = "LETTER_REQUEST_APPROVAL";
    public static final String WORKFLOW_ENTITY = "LetterRequest";
    private static final String MODULE = "LETTERS";
    private static final String ENTITY = "LetterRequest";

    private final LetterRequestRepository requestRepo;
    private final LetterTemplateRepository templateRepo;
    private final EmployeeRepository employees;
    private final LetterRenderer renderer;
    private final WorkflowService workflows;
    private final AuditService audit;
    private final AccessScopeService scope;
    private final CurrentRequest currentRequest;

    public LetterRequestService(LetterRequestRepository requestRepo,
                                 LetterTemplateRepository templateRepo,
                                 EmployeeRepository employees,
                                 LetterRenderer renderer,
                                 WorkflowService workflows,
                                 AuditService audit,
                                 AccessScopeService scope,
                                 CurrentRequest currentRequest) {
        this.requestRepo = requestRepo;
        this.templateRepo = templateRepo;
        this.employees = employees;
        this.renderer = renderer;
        this.workflows = workflows;
        this.audit = audit;
        this.scope = scope;
        this.currentRequest = currentRequest;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LetterRequest get(UUID id) {
        LetterRequest r = requestRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LetterRequest not found: " + id));
        if (!scope.isAccessible(r.getEmployeeId())) {
            throw new ResourceNotFoundException("LetterRequest not found: " + id);
        }
        return r;
    }

    @Transactional(readOnly = true)
    public Page<LetterRequest> list(LetterStatus status, Pageable pageable) {
        var s = scope.scopeForCurrentUser();
        if (s.isEmpty()) {
            return status == null
                    ? requestRepo.findAllByOrderByRequestedAtDesc(pageable)
                    : requestRepo.findByStatusOrderByRequestedAtDesc(status, pageable);
        }
        var ids = s.get();
        if (ids.isEmpty()) return Page.empty(pageable);
        return status == null
                ? requestRepo.findByEmployeeIdInOrderByRequestedAtDesc(ids, pageable)
                : requestRepo.findByEmployeeIdInAndStatusOrderByRequestedAtDesc(ids, status, pageable);
    }

    @Transactional(readOnly = true)
    public java.util.List<LetterRequest> mine(UUID employeeId) {
        return requestRepo.findByEmployeeIdOrderByRequestedAtDesc(employeeId);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Transactional
    public LetterRequest submit(LetterSubmitRequest req) {
        Employee employee = employees.findById(req.employeeId())
                .orElseThrow(() -> new BadRequestException(
                        "Employee not found: " + req.employeeId()));
        LetterTemplate template = templateRepo.findById(req.templateId())
                .orElseThrow(() -> new BadRequestException(
                        "LetterTemplate not found: " + req.templateId()));
        if (!template.isActive()) {
            throw new BadRequestException("Template is not active: " + template.getCode());
        }

        LetterRequest r = new LetterRequest();
        r.setRequestNo(String.format("LR-%06d", requestRepo.nextRequestNoSequence()));
        r.setEmployeeId(employee.getId());
        r.setTemplateId(template.getId());
        r.setPurpose(req.purpose());
        r.setCustomFieldsJson(req.customFields());
        r.setStatus(template.isRequiresApproval() ? LetterStatus.PENDING : LetterStatus.ISSUED);
        r.setCreatedBy(currentRequest.username());
        r.setUpdatedBy(currentRequest.username());

        if (!template.isRequiresApproval()) {
            // Auto-issue path — render now.
            r.setRenderedBody(renderer.render(template.getBody(), employee, asMap(req.customFields())));
            r.setIssuedAt(OffsetDateTime.now());
        }

        LetterRequest saved = requestRepo.save(r);

        if (template.isRequiresApproval()) {
            WorkflowInstance wf = workflows.start(new StartWorkflowRequest(
                    WORKFLOW_DEFINITION,
                    MODULE,
                    WORKFLOW_ENTITY,
                    saved.getId().toString(),
                    "Letter request " + saved.getRequestNo()
                            + " (" + template.getCode() + ")",
                    Map.of(
                            "templateCode", template.getCode(),
                            "employeeId", employee.getId().toString(),
                            "purpose", saved.getPurpose() == null ? "" : saved.getPurpose())));
            saved.setWorkflowInstanceId(wf.getId());
            saved = requestRepo.save(saved);
        }

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, LetterRequestResponse.from(saved));
        return saved;
    }

    @Transactional
    public LetterRequest onApproved(UUID id, String approver, String comment) {
        LetterRequest r = requestRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LetterRequest not found: " + id));
        if (r.getStatus() != LetterStatus.PENDING) {
            return r; // idempotent
        }
        LetterRequestResponse before = LetterRequestResponse.from(r);
        r.setStatus(LetterStatus.APPROVED);
        r.setDecidedBy(approver);
        r.setDecisionComment(comment);
        // Render immediately on approval — the body becomes ISSUED data.
        Employee employee = employees.findById(r.getEmployeeId())
                .orElseThrow(() -> new IllegalStateException(
                        "Employee vanished mid-flow: " + r.getEmployeeId()));
        LetterTemplate template = templateRepo.findById(r.getTemplateId())
                .orElseThrow(() -> new IllegalStateException(
                        "Template vanished mid-flow: " + r.getTemplateId()));
        r.setRenderedBody(renderer.render(
                template.getBody(), employee, asMap(r.getCustomFieldsJson())));
        r.setStatus(LetterStatus.ISSUED);
        r.setIssuedAt(OffsetDateTime.now());
        r.setUpdatedBy(approver == null ? currentRequest.username() : approver);
        LetterRequest saved = requestRepo.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "APPROVE", before, LetterRequestResponse.from(saved));
        return saved;
    }

    @Transactional
    public LetterRequest onRejected(UUID id, String approver, String comment) {
        return transition(id, LetterStatus.REJECTED, approver, comment, "REJECT");
    }

    @Transactional
    public LetterRequest onCancelled(UUID id, String approver, String comment) {
        return transition(id, LetterStatus.CANCELLED, approver, comment, "CANCEL");
    }

    private LetterRequest transition(UUID id, LetterStatus newStatus,
                                      String approver, String comment, String auditAction) {
        LetterRequest r = requestRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LetterRequest not found: " + id));
        if (r.getStatus() == newStatus) return r;
        LetterRequestResponse before = LetterRequestResponse.from(r);
        r.setStatus(newStatus);
        r.setDecidedBy(approver);
        r.setDecisionComment(comment);
        r.setUpdatedBy(approver == null ? currentRequest.username() : approver);
        LetterRequest saved = requestRepo.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                auditAction, before, LetterRequestResponse.from(saved));
        return saved;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object json) {
        if (json instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }
}
