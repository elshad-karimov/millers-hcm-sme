package az.millers.hcm.compensation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.api.dto.CreateSalaryChangeRequest;
import az.millers.hcm.compensation.api.dto.SalaryChangeRequestDto;
import az.millers.hcm.compensation.domain.CompConfig;
import az.millers.hcm.compensation.domain.CompensationExceptionSeverity;
import az.millers.hcm.compensation.domain.CompensationExceptionSource;
import az.millers.hcm.compensation.domain.CompensationExceptionType;
import az.millers.hcm.compensation.domain.PayBand;
import az.millers.hcm.compensation.domain.SalaryChangeRequest;
import az.millers.hcm.compensation.domain.SalaryChangeStatus;
import az.millers.hcm.compensation.repo.SalaryChangeRequestRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.staffing.domain.Grade;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.GradeRepository;
import az.millers.hcm.staffing.repo.PositionRepository;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * M361 — Salary change request + approval workflow.
 * A salary change must be APPROVED before it becomes the employee's compensation.
 * Reuses the existing workflow engine.
 */
@Service
public class SalaryChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(SalaryChangeRequestService.class);

    public static final String WORKFLOW_DEFINITION = "SALARY_CHANGE_APPROVAL";
    private static final String MODULE = "compensation";
    private static final String ENTITY = "SalaryChangeRequest";
    private static final String TENANT = "default";

    private final SalaryChangeRequestRepository repo;
    private final EmployeeRepository employees;
    private final CompensationService compensations;
    private final CompConfigService compConfig;
    private final PayBandService payBandService;
    private final PositionRepository positions;
    private final GradeRepository grades;
    private final CompensationExceptionService exceptionService;
    private final WorkflowService workflowService;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final TransactionTemplate requiresNew;

    public SalaryChangeRequestService(SalaryChangeRequestRepository repo,
                                       EmployeeRepository employees,
                                       CompensationService compensations,
                                       CompConfigService compConfig,
                                       PayBandService payBandService,
                                       PositionRepository positions,
                                       GradeRepository grades,
                                       CompensationExceptionService exceptionService,
                                       WorkflowService workflowService,
                                       AccessScopeService accessScope,
                                       AuditService audit,
                                       CurrentRequest currentRequest,
                                       PlatformTransactionManager txManager) {
        this.repo = repo;
        this.employees = employees;
        this.compensations = compensations;
        this.compConfig = compConfig;
        this.payBandService = payBandService;
        this.positions = positions;
        this.grades = grades;
        this.exceptionService = exceptionService;
        this.workflowService = workflowService;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public SalaryChangeRequest create(CreateSalaryChangeRequest req) {
        if (req.proposedSalary() == null || req.proposedSalary().signum() < 0) {
            throw new BadRequestException("proposedSalary must be non-negative");
        }
        if (req.effectiveDate() == null) {
            throw new BadRequestException("effectiveDate is required");
        }

        Employee emp = employees.findById(req.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.employeeId()));

        // Look up current salary
        BigDecimal currentSalary = null;
        String currency = req.currency() != null ? req.currency() : "AZN";
        try {
            EmployeeCompensation current = compensations.getActiveOn(req.employeeId(), LocalDate.now());
            currentSalary = current.getMonthlyBaseSalary();
            currency = current.getCurrency();
        } catch (Exception e) {
            // No current compensation — current salary is null
        }

        // Calculate increase percentage
        BigDecimal increasePct = null;
        if (currentSalary != null && currentSalary.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal increase = req.proposedSalary().subtract(currentSalary);
            increasePct = increase.divide(currentSalary, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Band check: find applicable pay band for the employee's grade
        boolean outsideBand = false;
        Grade grade = resolveGrade(emp);
        if (grade != null) {
            List<PayBand> bands = payBandService.findActiveOnForGrade(grade.getId(), LocalDate.now());
            if (!bands.isEmpty()) {
                PayBand band = bands.get(0);
                if (req.proposedSalary().compareTo(band.getMinSalary()) < 0
                        || req.proposedSalary().compareTo(band.getMaxSalary()) > 0) {
                    outsideBand = true;
                }
            }
        }

        // Threshold check
        CompConfig config = compConfig.get();
        boolean aboveThreshold = false;
        if (increasePct != null && config.getMaxIncreasePctWithoutApproval() != null) {
            aboveThreshold = increasePct.compareTo(config.getMaxIncreasePctWithoutApproval()) > 0;
        }

        SalaryChangeRequest scr = new SalaryChangeRequest();
        scr.setTenantId(TENANT);
        scr.setEmployeeId(req.employeeId());
        scr.setCurrentSalary(currentSalary);
        scr.setProposedSalary(req.proposedSalary());
        scr.setCurrency(currency);
        scr.setChangeReasonId(req.changeReasonId());
        scr.setEffectiveDate(req.effectiveDate());
        scr.setIncreasePct(increasePct);
        scr.setOutsideBand(outsideBand);
        scr.setAboveThreshold(aboveThreshold);
        scr.setStatus(SalaryChangeStatus.DRAFT);
        scr.setRequestedBy(currentRequest.username());
        scr.setNote(req.note());

        SalaryChangeRequest saved = repo.save(scr);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATED", null, SalaryChangeRequestDto.from(saved));

        // Auto-raise exceptions for out-of-band / above-threshold
        if (outsideBand) {
            PayBand band = payBandService.findActiveOnForGrade(grade.getId(), LocalDate.now()).get(0);
            CompensationExceptionType exType = req.proposedSalary().compareTo(band.getMinSalary()) < 0
                    ? CompensationExceptionType.BELOW_BAND
                    : CompensationExceptionType.ABOVE_BAND;
            exceptionService.raise(req.employeeId(), CompensationExceptionSource.SALARY_CHANGE,
                    saved.getId(), exType, CompensationExceptionSeverity.MEDIUM,
                    "Proposed salary outside pay band range");
        }
        if (aboveThreshold) {
            exceptionService.raise(req.employeeId(), CompensationExceptionSource.SALARY_CHANGE,
                    saved.getId(), CompensationExceptionType.ABOVE_THRESHOLD, CompensationExceptionSeverity.MEDIUM,
                    "Increase percentage exceeds threshold (" + increasePct + "% > "
                            + config.getMaxIncreasePctWithoutApproval() + "%)");
        }

        return saved;
    }

    @Transactional
    public SalaryChangeRequest submit(UUID id) {
        SalaryChangeRequest scr = get(id);
        if (scr.getStatus() != SalaryChangeStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT requests can be submitted (current: " + scr.getStatus() + ")");
        }

        Employee emp = employees.findById(scr.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + scr.getEmployeeId()));

        // Block self-approval: if the employee's manager is the requester, reject
        // (The workflow engine also blocks this, but we validate at submit time)
        if (emp.getManagerId() != null) {
            Employee manager = employees.findById(emp.getManagerId()).orElse(null);
            if (manager != null && manager.getUsername() != null
                    && manager.getUsername().equals(scr.getRequestedBy())) {
                throw new BadRequestException(
                        "Employee cannot approve their own compensation change (self-approval blocked)");
            }
        }

        // Start workflow
        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                scr.getId().toString(),
                "Salary change for " + emp.getFirstName() + " " + emp.getLastName()
                        + " (" + scr.getProposedSalary() + " " + scr.getCurrency() + ")",
                Map.of(
                        "employeeId", scr.getEmployeeId().toString(),
                        "employeeNo", emp.getEmployeeNo(),
                        "proposedSalary", scr.getProposedSalary().toPlainString(),
                        "currency", scr.getCurrency(),
                        "effectiveDate", scr.getEffectiveDate().toString()
                )
        ));

        scr.setWorkflowInstanceId(instance.getId());
        scr.setStatus(SalaryChangeStatus.PENDING_APPROVAL);
        SalaryChangeRequest saved = repo.save(scr);

        audit.record(MODULE, ENTITY, id.toString(), "SUBMITTED", null,
                Map.of("workflowInstanceId", instance.getId().toString()));
        return saved;
    }

    @Transactional(readOnly = true)
    public SalaryChangeRequest get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salary change request not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SalaryChangeRequestDto> list(UUID employeeId, SalaryChangeStatus status) {
        List<SalaryChangeRequest> rows;
        if (employeeId != null) {
            // Scope-aware: only return if employee is accessible
            if (!accessScope.isAccessible(employeeId)) {
                return List.of();
            }
            rows = repo.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId);
        } else if (status != null) {
            rows = repo.findByTenantIdAndStatusOrderByCreatedAtDesc(TENANT, status);
        } else {
            rows = repo.findByTenantIdOrderByCreatedAtDesc(TENANT);
        }
        return rows.stream().map(SalaryChangeRequestDto::from).toList();
    }

    @Transactional
    public SalaryChangeRequest cancel(UUID id) {
        SalaryChangeRequest scr = get(id);
        if (scr.getStatus() != SalaryChangeStatus.DRAFT && scr.getStatus() != SalaryChangeStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only DRAFT or PENDING_APPROVAL requests can be cancelled");
        }

        // Cancel the workflow if running
        if (scr.getWorkflowInstanceId() != null) {
            try {
                workflowService.act(scr.getWorkflowInstanceId(),
                        new az.millers.hcm.workflow.api.dto.ActionRequest(
                                az.millers.hcm.workflow.domain.ActionType.CANCEL, "Cancelled by requestor", null, null));
            } catch (Exception e) {
                log.warn("Failed to cancel workflow {}: {}", scr.getWorkflowInstanceId(), e.getMessage());
            }
        }

        scr.setStatus(SalaryChangeStatus.CANCELLED);
        SalaryChangeRequest saved = repo.save(scr);
        audit.record(MODULE, ENTITY, id.toString(), "CANCELLED", null,
                SalaryChangeRequestDto.from(saved));
        return saved;
    }

    /**
     * Workflow completion listener (AFTER_COMMIT).
     * On APPROVED → apply the change to EmployeeCompensation.
     * On REJECTED → mark rejected.
     * Uses REQUIRES_NEW transaction per the OnboardingTrainingService pattern.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!ENTITY.equals(event.subjectEntity())) return;

        UUID requestId = UUID.fromString(event.subjectId());
        requiresNew.executeWithoutResult(status -> {
            SalaryChangeRequest scr = repo.findById(requestId).orElse(null);
            if (scr == null) {
                log.warn("Salary change request not found for workflow completion: {}", requestId);
                return;
            }

            // Idempotent: if already APPLIED, do nothing
            if (scr.getStatus() == SalaryChangeStatus.APPLIED) {
                return;
            }

            switch (event.status()) {
                case APPROVED, AUTO_APPROVED -> {
                    // Guard: do not apply for TERMINATED employee
                    Employee emp = employees.findById(scr.getEmployeeId()).orElse(null);
                    if (emp != null && emp.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
                        log.warn("Salary change request {} approved but employee {} is TERMINATED — not applying",
                                requestId, scr.getEmployeeId());
                        scr.setStatus(SalaryChangeStatus.APPROVED);
                        scr.setDecidedAt(OffsetDateTime.now());
                        repo.save(scr);
                        audit.record(MODULE, ENTITY, requestId.toString(), "APPROVED_NOT_APPLIED",
                                null, "Employee is TERMINATED");
                        return;
                    }

                    scr.setStatus(SalaryChangeStatus.APPROVED);
                    scr.setDecidedAt(OffsetDateTime.now());

                    // Apply: write to EmployeeCompensation (effective-dated, never overwrite)
                    EmployeeCompensation newComp = new EmployeeCompensation();
                    newComp.setEmployeeId(scr.getEmployeeId());
                    newComp.setMonthlyBaseSalary(scr.getProposedSalary());
                    newComp.setCurrency(scr.getCurrency());
                    newComp.setEffectiveFrom(scr.getEffectiveDate());
                    newComp.setReason("Approved salary change request " + scr.getId());
                    compensations.upsert(newComp);

                    scr.setStatus(SalaryChangeStatus.APPLIED);
                    scr.setAppliedAt(OffsetDateTime.now());
                    repo.save(scr);

                    audit.record(MODULE, ENTITY, requestId.toString(), "APPROVED", null,
                            SalaryChangeRequestDto.from(scr));
                    audit.record(MODULE, ENTITY, requestId.toString(), "APPLIED", null,
                            SalaryChangeRequestDto.from(scr));

                    log.info("Salary change request {} approved and applied for employee {}",
                            requestId, scr.getEmployeeId());
                }
                case REJECTED, RETURNED -> {
                    scr.setStatus(SalaryChangeStatus.REJECTED);
                    scr.setDecidedAt(OffsetDateTime.now());
                    repo.save(scr);
                    audit.record(MODULE, ENTITY, requestId.toString(), "REJECTED", null,
                            SalaryChangeRequestDto.from(scr));
                    log.info("Salary change request {} rejected", requestId);
                }
                case CANCELLED -> {
                    scr.setStatus(SalaryChangeStatus.CANCELLED);
                    repo.save(scr);
                    audit.record(MODULE, ENTITY, requestId.toString(), "CANCELLED", null,
                            SalaryChangeRequestDto.from(scr));
                }
                default -> log.warn("Unexpected workflow status {} for salary change request {}",
                        event.status(), requestId);
            }
        });
    }

    private Grade resolveGrade(Employee emp) {
        if (emp.getPositionId() == null) return null;
        Position pos = positions.findById(emp.getPositionId()).orElse(null);
        if (pos == null || pos.getGradeId() == null) return null;
        return grades.findById(pos.getGradeId()).orElse(null);
    }
}
