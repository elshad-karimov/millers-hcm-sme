package az.millers.hcm.recruitment.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.VacancyRequest;
import az.millers.hcm.recruitment.api.dto.VacancyResponse;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.domain.VacancyStatus;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.service.PositionHeadcountService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;
import az.millers.hcm.common.BusinessNumbers;

@Service
public class VacancyService {

    private static final Logger log = LoggerFactory.getLogger(VacancyService.class);

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Vacancy";

    /** M275 — Recruitment PRD §7: definition codes seeded in V143. */
    public static final String WORKFLOW_NEW = "REQUISITION_APPROVAL_NEW";
    public static final String WORKFLOW_REPLACEMENT = "REQUISITION_APPROVAL_REPLACEMENT";

    /**
     * M275 — states the generic changeStatus endpoint may NOT enter.
     * DRAFT/PENDING_APPROVAL/APPROVED/REJECTED belong to the approval
     * state machine (submitForApproval + workflow outcome listener);
     * letting changeStatus jump into them would bypass approval.
     */
    private static final Set<VacancyStatus> WORKFLOW_OWNED =
            Set.of(VacancyStatus.DRAFT, VacancyStatus.PENDING_APPROVAL,
                   VacancyStatus.APPROVED, VacancyStatus.REJECTED);

    private final VacancyRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final PositionHeadcountService headcountGate;
    private final WorkflowService workflowService;
    // M277 — confidential requisition visibility (Recruitment PRD §41).
    private final az.millers.hcm.security.scope.AccessScopeService accessScope;
    private final az.millers.hcm.corehr.repo.EmployeeRepository employees;

    public VacancyService(VacancyRepository repository, AuditService audit,
                          CurrentRequest currentRequest,
                          PositionHeadcountService headcountGate,
                          WorkflowService workflowService,
                          az.millers.hcm.security.scope.AccessScopeService accessScope,
                          az.millers.hcm.corehr.repo.EmployeeRepository employees) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.headcountGate = headcountGate;
        this.workflowService = workflowService;
        this.accessScope = accessScope;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public Vacancy get(UUID id) {
        Vacancy v = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vacancy not found: " + id));
        // M277 — confidential requisitions 404 (not 403) for outsiders so
        // their very existence stays hidden.
        if (v.isConfidential() && !canViewConfidential(v)) {
            throw new ResourceNotFoundException("Vacancy not found: " + id);
        }
        return v;
    }

    @Transactional(readOnly = true)
    public Page<Vacancy> list(VacancyStatus status, Pageable pageable) {
        // M277 — confidential rows are filtered in-query so pagination
        // stays correct. The hiring team (named recruiter / hiring
        // manager) and unrestricted users see everything.
        boolean unrestricted = accessScope.isUnrestricted();
        UUID employeeId = currentEmployeeIdOrNull();
        if (status != null) {
            return repository.findByStatusVisible(status, unrestricted, employeeId, pageable);
        }
        return repository.findAllVisible(unrestricted, employeeId, pageable);
    }

    /** M277 — true when the caller may see this confidential requisition. */
    private boolean canViewConfidential(Vacancy v) {
        if (accessScope.isUnrestricted()) return true;
        UUID employeeId = currentEmployeeIdOrNull();
        if (employeeId == null) return false;
        return employeeId.equals(v.getRecruiterId())
                || employeeId.equals(v.getHiringManagerId());
    }

    /** The caller's linked employee id, or null when no link exists. */
    private UUID currentEmployeeIdOrNull() {
        String username = currentRequest.username();
        if (username == null || username.isBlank()) return null;
        return employees.findByUsername(username)
                .map(az.millers.hcm.corehr.domain.Employee::getId)
                .orElse(null);
    }

    /**
     * M275 — requisitions now start life in DRAFT and must travel
     * DRAFT → PENDING_APPROVAL → APPROVED → OPEN before accepting
     * candidates (PRD §7: "Requisition cannot be published before
     * approval").
     */
    @Transactional
    public Vacancy create(VacancyRequest req) {
        return doCreate(req, VacancyStatus.DRAFT);
    }

    /**
     * M275 — auto-post path (M242 listener). The vacancy is born from
     * an APPROVED headcount change: the approval already happened in
     * the HEADCOUNT_CHANGE_REQUEST workflow, so a second requisition
     * approval would be pure ceremony. Created directly OPEN.
     */
    @Transactional
    public Vacancy createOpen(VacancyRequest req) {
        return doCreate(req, VacancyStatus.OPEN);
    }

    private Vacancy doCreate(VacancyRequest req, VacancyStatus initial) {
        // M109 — refuse to post requisitions for a position that has no room.
        int openings = req.openings() == null ? 1 : req.openings();
        headcountGate.assertCanPostVacancy(req.positionId(), openings);

        Vacancy v = new Vacancy();
        v.setVacancyNo(BusinessNumbers.format("VAC", 5, repository.nextNoSequence()));
        v.setStatus(initial);
        v.setCreatedBy(currentRequest.username());
        v.setUpdatedBy(currentRequest.username());
        applyRequest(v, req);
        Vacancy saved = repository.save(v);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, VacancyResponse.from(saved));
        return saved;
    }

    @Transactional
    public Vacancy update(UUID id, VacancyRequest req) {
        Vacancy v = get(id);
        // M275 — a requisition under approval is frozen: edits would
        // make approvers sign off on content they never saw.
        if (v.getStatus() == VacancyStatus.PENDING_APPROVAL) {
            throw new BadRequestException(
                    "Requisition is pending approval — wait for the decision before editing");
        }
        VacancyResponse before = VacancyResponse.from(v);
        applyRequest(v, req);
        // Editing an APPROVED or REJECTED requisition invalidates the
        // decision — back to DRAFT for re-submission.
        if (v.getStatus() == VacancyStatus.APPROVED || v.getStatus() == VacancyStatus.REJECTED) {
            v.setStatus(VacancyStatus.DRAFT);
        }
        v.setUpdatedBy(currentRequest.username());
        Vacancy saved = repository.save(v);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, VacancyResponse.from(saved));
        return saved;
    }

    // ── M275 — approval state machine ──────────────────────────────────

    @Transactional
    public Vacancy submitForApproval(UUID id) {
        Vacancy v = get(id);
        if (v.getStatus() != VacancyStatus.DRAFT && v.getStatus() != VacancyStatus.REJECTED) {
            throw new BadRequestException(
                    "Only DRAFT or REJECTED requisitions can be submitted (current: "
                            + v.getStatus() + ")");
        }

        // Replacement-like requisitions take the short chain — the
        // headcount cost already exists.
        String definition = v.getRequisitionType().isReplacementLike()
                ? WORKFLOW_REPLACEMENT
                : WORKFLOW_NEW;

        String title = v.getVacancyNo() + " — " + v.getTitle()
                + " (" + v.getOpenings() + " opening" + (v.getOpenings() > 1 ? "s" : "") + ")";

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                definition,
                MODULE,
                ENTITY,
                v.getId().toString(),
                title,
                Map.of(
                        "vacancyNo", v.getVacancyNo(),
                        "title", v.getTitle(),
                        "requisitionType", v.getRequisitionType().name(),
                        "openings", v.getOpenings(),
                        "department", v.getDepartment() == null ? "" : v.getDepartment(),
                        "requestedBy", currentRequest.username())));

        VacancyStatus old = v.getStatus();
        v.setStatus(VacancyStatus.PENDING_APPROVAL);
        v.setWorkflowInstanceId(instance.getId());
        v.setUpdatedBy(currentRequest.username());
        Vacancy saved = repository.save(v);
        audit.record(MODULE, ENTITY, id.toString(), "SUBMIT_FOR_APPROVAL",
                Map.of("status", old.name()),
                Map.of("status", saved.getStatus().name(),
                        "workflowInstanceId", instance.getId().toString(),
                        "definition", definition));
        return saved;
    }

    /** M275 — reacts to the requisition approval workflow finishing. */
    @EventListener
    @Transactional
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!WORKFLOW_NEW.equals(event.definitionCode())
                && !WORKFLOW_REPLACEMENT.equals(event.definitionCode())) {
            return;
        }
        if (!ENTITY.equals(event.subjectEntity())) return;

        UUID vacancyId;
        try {
            vacancyId = UUID.fromString(event.subjectId());
        } catch (IllegalArgumentException e) {
            log.warn("Requisition approval: invalid subjectId '{}'", event.subjectId());
            return;
        }
        Vacancy v = repository.findById(vacancyId).orElse(null);
        if (v == null) {
            log.warn("Requisition approval: vacancy {} not found for workflow {}",
                    vacancyId, event.instanceId());
            return;
        }
        if (v.getStatus() != VacancyStatus.PENDING_APPROVAL) return; // idempotent guard

        VacancyStatus target = switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> VacancyStatus.APPROVED;
            case REJECTED                -> VacancyStatus.REJECTED;
            // RETURNED = "fix and resubmit" → back to DRAFT for editing;
            // CANCELLED = withdrawn → also DRAFT.
            default                      -> VacancyStatus.DRAFT;
        };
        v.setStatus(target);
        v.setUpdatedBy(event.actor());
        repository.save(v);
        audit.record(MODULE, ENTITY, vacancyId.toString(), "APPROVAL_OUTCOME",
                Map.of("status", VacancyStatus.PENDING_APPROVAL.name()),
                Map.of("status", target.name(),
                        "workflowStatus", event.status().name(),
                        "actor", event.actor() == null ? "" : event.actor(),
                        "comment", event.comment() == null ? "" : event.comment()));
        log.info("Requisition {} approval outcome: {} → {} (by {})",
                v.getVacancyNo(), event.status(), target, event.actor());
    }

    @Transactional
    public Vacancy changeStatus(UUID id, VacancyStatus newStatus, String reason) {
        Vacancy v = get(id);
        if (v.getStatus() == newStatus) {
            throw new BadRequestException("Vacancy already " + newStatus);
        }
        // M275 — approval-side states are owned by the workflow.
        if (WORKFLOW_OWNED.contains(newStatus)) {
            throw new BadRequestException(
                    "Status " + newStatus + " is managed by the approval workflow — "
                            + "use submit-for-approval instead");
        }
        if (v.getStatus() == VacancyStatus.PENDING_APPROVAL) {
            throw new BadRequestException(
                    "Requisition is pending approval — wait for the decision");
        }
        // Opening/publishing requires the requisition to have passed
        // approval (or be coming back from a paused state).
        if (newStatus.isAccepting()
                && v.getStatus() != VacancyStatus.APPROVED
                && !v.getStatus().isAccepting()
                && v.getStatus() != VacancyStatus.PAUSED
                && v.getStatus() != VacancyStatus.ON_HOLD) {
            throw new BadRequestException(
                    "Requisition must be APPROVED before it can be opened (current: "
                            + v.getStatus() + ")");
        }
        // M275 — re-run the M109 headcount gate when (re)opening: the
        // position may have filled up while the requisition sat in
        // approval or on pause.
        if (newStatus.isAccepting() && !v.getStatus().isAccepting()) {
            headcountGate.assertCanPostVacancy(v.getPositionId(), v.getOpenings());
        }

        VacancyStatus old = v.getStatus();
        v.setStatus(newStatus);
        v.setUpdatedBy(currentRequest.username());
        Vacancy saved = repository.save(v);
        audit.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGE",
                Map.of("status", old.name()),
                Map.of("status", newStatus.name(),
                        "reason", reason == null ? "" : reason));
        return saved;
    }

    private void applyRequest(Vacancy v, VacancyRequest req) {
        v.setTitle(req.title());
        v.setPositionId(req.positionId());
        v.setDepartment(req.department());
        v.setLocation(req.location());
        v.setOpenings(req.openings() == null ? 1 : req.openings());
        v.setDescription(req.description());
        v.setRequirements(req.requirements());
        if (req.salaryMin() != null && req.salaryMax() != null
                && req.salaryMin().compareTo(req.salaryMax()) > 0) {
            throw new BadRequestException("salaryMin cannot be greater than salaryMax");
        }
        v.setSalaryMin(req.salaryMin());
        v.setSalaryMax(req.salaryMax());
        v.setCurrency(req.currency() == null ? "AZN" : req.currency().toUpperCase());
        v.setHiringManagerId(req.hiringManagerId());
        v.setRecruiterId(req.recruiterId());
        v.setOpeningDate(req.openingDate());
        v.setClosingDate(req.closingDate());
        // M274 — requisition fields. Type defaults to NEW_HEADCOUNT so
        // pre-M274 clients that don't send it keep working.
        v.setRequisitionType(req.requisitionType() == null
                ? az.millers.hcm.recruitment.domain.RequisitionType.NEW_HEADCOUNT
                : req.requisitionType());
        v.setHiringReason(req.hiringReason());
        v.setTargetStartDate(req.targetStartDate());
        v.setCostCentre(req.costCentre());
        v.setEmploymentType(req.employmentType());
        v.setReplacedEmployeeId(req.replacedEmployeeId());
        // M277 — null means "not sent" (old clients): keep current value.
        if (req.confidential() != null) {
            v.setConfidential(req.confidential());
        }
    }
}
