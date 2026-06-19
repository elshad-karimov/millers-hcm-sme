package az.millers.hcm.lifecycle.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.api.dto.ResignationResponse;
import az.millers.hcm.lifecycle.api.dto.ResignationSubmitRequest;
import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.domain.OffboardingCaseStatus;
import az.millers.hcm.lifecycle.domain.OffboardingSource;
import az.millers.hcm.lifecycle.domain.ResignationRequest;
import az.millers.hcm.lifecycle.domain.ResignationStatus;
import az.millers.hcm.lifecycle.repo.OffboardingCaseRepository;
import az.millers.hcm.lifecycle.repo.ResignationRequestRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

@Service
public class ResignationService {

    public static final String WORKFLOW_DEFINITION = "RESIGNATION_APPROVAL";
    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "ResignationRequest";

    private static final Logger log = LoggerFactory.getLogger(ResignationService.class);

    private static final List<OffboardingCaseStatus> TERMINAL_CASE_STATUSES = List.of(
            OffboardingCaseStatus.COMPLETED, OffboardingCaseStatus.CANCELLED,
            OffboardingCaseStatus.REVERSED);

    private final ResignationRequestRepository resignations;
    private final OffboardingCaseRepository offboardingCases;
    private final EmployeeRepository employees;
    private final WorkflowService workflowService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ResignationService(ResignationRequestRepository resignations,
                               OffboardingCaseRepository offboardingCases,
                               EmployeeRepository employees,
                               WorkflowService workflowService,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.resignations = resignations;
        this.offboardingCases = offboardingCases;
        this.employees = employees;
        this.workflowService = workflowService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public ResignationRequest get(UUID id) {
        return resignations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resignation not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ResignationRequest> list(UUID employeeId, ResignationStatus status, Pageable pageable) {
        if (employeeId != null) {
            return resignations.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
        }
        if (status != null) {
            return resignations.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return resignations.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public ResignationRequest submit(ResignationSubmitRequest req) {
        Employee employee = employees.findById(req.employeeId())
                .orElseThrow(() -> new BadRequestException("Employee not found: " + req.employeeId()));
        if (employee.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new BadRequestException("Employee is already terminated");
        }
        if (req.proposedLastWorkingDate().isBefore(req.resignationDate())) {
            throw new BadRequestException("proposedLastWorkingDate cannot be before resignationDate");
        }
        if (offboardingCases.existsByEmployeeIdAndCaseStatusNotIn(req.employeeId(), TERMINAL_CASE_STATUSES)) {
            throw new BadRequestException("An active offboarding case already exists for this employee");
        }

        ResignationRequest r = new ResignationRequest();
        r.setResignationNo(String.format("RES-%05d", resignations.nextNoSequence()));
        r.setEmployeeId(req.employeeId());
        r.setResignationDate(req.resignationDate());
        r.setProposedLastWorkingDate(req.proposedLastWorkingDate());
        r.setReasonText(req.reasonText());
        r.setComments(req.comments());
        r.setStatus(ResignationStatus.SUBMITTED);
        r.setCreatedBy(currentRequest.username());
        ResignationRequest saved = resignations.save(r);

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                saved.getId().toString(),
                saved.getResignationNo() + " — " + employee.getFirstName() + " " + employee.getLastName()
                        + " (proposed LWD: " + saved.getProposedLastWorkingDate() + ")",
                Map.of(
                        "employeeNo", employee.getEmployeeNo(),
                        "employeeName", employee.getFirstName() + " " + employee.getLastName(),
                        "resignationDate", saved.getResignationDate().toString(),
                        "proposedLastWorkingDate", saved.getProposedLastWorkingDate().toString())));
        saved.setWorkflowInstanceId(instance.getId());
        saved = resignations.save(saved);

        OffboardingCase obCase = new OffboardingCase();
        obCase.setCaseNo(String.format("OFB-%05d", offboardingCases.nextNoSequence()));
        obCase.setEmployeeId(req.employeeId());
        obCase.setSource(OffboardingSource.RESIGNATION);
        obCase.setResignationId(saved.getId());
        obCase.setExitReason(req.reasonText());
        obCase.setLastWorkingDate(req.proposedLastWorkingDate());
        obCase.setCaseStatus(OffboardingCaseStatus.PENDING_APPROVAL);
        obCase.setCreatedBy(currentRequest.username());
        offboardingCases.save(obCase);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "SUBMIT", null, ResignationResponse.from(saved));
        return saved;
    }

    @Transactional
    public ResignationRequest onApproved(UUID id, String comment) {
        ResignationRequest r = get(id);
        if (r.getStatus() != ResignationStatus.SUBMITTED) return r;
        r.setStatus(ResignationStatus.APPROVED);
        r.setApprovedLastWorkingDate(r.getProposedLastWorkingDate());
        ResignationRequest saved = resignations.save(r);
        offboardingCases.findByResignationId(id).ifPresent(c -> {
            c.setCaseStatus(OffboardingCaseStatus.IN_PROGRESS);
            c.setLastWorkingDate(saved.getApprovedLastWorkingDate());
            offboardingCases.save(c);
        });
        audit.record(MODULE, ENTITY, id.toString(), "APPROVED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public ResignationRequest onRejected(UUID id, String comment) {
        ResignationRequest r = get(id);
        if (r.getStatus() != ResignationStatus.SUBMITTED) return r;
        r.setStatus(ResignationStatus.REJECTED);
        ResignationRequest saved = resignations.save(r);
        offboardingCases.findByResignationId(id).ifPresent(c -> {
            c.setCaseStatus(OffboardingCaseStatus.CANCELLED);
            offboardingCases.save(c);
        });
        audit.record(MODULE, ENTITY, id.toString(), "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public ResignationRequest onCancelled(UUID id, String comment) {
        ResignationRequest r = get(id);
        if (r.getStatus() != ResignationStatus.SUBMITTED) return r;
        r.setStatus(ResignationStatus.CANCELLED);
        ResignationRequest saved = resignations.save(r);
        offboardingCases.findByResignationId(id).ifPresent(c -> {
            c.setCaseStatus(OffboardingCaseStatus.CANCELLED);
            offboardingCases.save(c);
        });
        audit.record(MODULE, ENTITY, id.toString(), "CANCELLED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public ResignationRequest withdraw(UUID id) {
        ResignationRequest r = get(id);
        if (r.getStatus() != ResignationStatus.SUBMITTED && r.getStatus() != ResignationStatus.APPROVED) {
            throw new BadRequestException("Only SUBMITTED or APPROVED resignations can be withdrawn");
        }
        r.setStatus(ResignationStatus.WITHDRAWN);
        ResignationRequest saved = resignations.save(r);
        offboardingCases.findByResignationId(id).ifPresent(c -> {
            c.setCaseStatus(OffboardingCaseStatus.CANCELLED);
            offboardingCases.save(c);
        });
        audit.record(MODULE, ENTITY, id.toString(), "WITHDRAWN", null, null);
        return saved;
    }
}
