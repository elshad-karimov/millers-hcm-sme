package az.millers.hcm.payroll.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.service.AttendancePayrollSummaryService;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.api.dto.AddBonusRequest;
import az.millers.hcm.payroll.api.dto.CreateRunRequest;
import az.millers.hcm.payroll.domain.PayrollAllowance;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.repo.PayrollAllowanceRepository;
import az.millers.hcm.payroll.repo.PayrollBonusRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.payroll.event.PayrollRunPaidEvent;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;
import az.millers.hcm.common.BusinessNumbers;

@Service
public class PayrollRunService {

    public static final String WORKFLOW_DEFINITION = "PAYROLL_APPROVAL";
    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "PayrollRun";

    private final PayrollRunRepository runs;
    private final PayrollResultRepository results;
    private final PayrollBonusRepository bonuses;
    private final PayrollAllowanceRepository allowances;
    private final PayrollEngine engine;
    private final AttendancePayrollSummaryService attendanceSummaryService;
    private final WorkflowService workflowService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PayrollRunService(PayrollRunRepository runs,
                              PayrollResultRepository results,
                              PayrollBonusRepository bonuses,
                              PayrollAllowanceRepository allowances,
                              PayrollEngine engine,
                              AttendancePayrollSummaryService attendanceSummaryService,
                              WorkflowService workflowService,
                              ApplicationEventPublisher eventPublisher,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.runs = runs;
        this.results = results;
        this.bonuses = bonuses;
        this.allowances = allowances;
        this.engine = engine;
        this.attendanceSummaryService = attendanceSummaryService;
        this.workflowService = workflowService;
        this.eventPublisher = eventPublisher;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public PayrollRun get(UUID id) {
        return runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PayrollRun> list() {
        return runs.findAllByOrderByPeriodYearDescPeriodMonthDesc();
    }

    @Transactional(readOnly = true)
    public List<PayrollResult> resultsOf(UUID runId) {
        return results.findByRunIdOrderByEmployeeIdAsc(runId);
    }

    /**
     * M41: allowance lines snapshotted for one employee on one run.
     * Returned in {@code allowanceTypeCode} alphabetical order so the
     * payslip lays out predictably regardless of insertion order.
     */
    @Transactional(readOnly = true)
    public List<PayrollAllowance> allowancesOf(UUID runId, UUID employeeId) {
        return allowances.findByRunIdAndEmployeeIdOrderByAllowanceTypeCodeAsc(runId, employeeId);
    }

    /**
     * M41: full per-run allowance snapshot — useful for the run-detail
     * page's "total allowance" drilldown and for the bank-file / export
     * paths to enumerate every line.
     */
    @Transactional(readOnly = true)
    public List<PayrollAllowance> allowancesOfRun(UUID runId) {
        return allowances.findByRunIdOrderByEmployeeIdAscAllowanceTypeCodeAsc(runId);
    }

    @Transactional
    public PayrollRun create(CreateRunRequest req) {
        String jurisdiction = req.jurisdiction() == null ? "AZ" : req.jurisdiction();
        az.millers.hcm.payroll.domain.RunType runType = req.runType() == null
                ? az.millers.hcm.payroll.domain.RunType.REGULAR
                : req.runType();

        // Validation: OFF_CYCLE requires employeeIds
        if (runType == az.millers.hcm.payroll.domain.RunType.OFF_CYCLE) {
            if (req.employeeIds() == null || req.employeeIds().isEmpty()) {
                throw new BadRequestException("OFF_CYCLE_REQUIRES_EMPLOYEES");
            }
        }

        // REGULAR runs still enforce uniqueness; OFF_CYCLE runs bypass this guard
        if (runType == az.millers.hcm.payroll.domain.RunType.REGULAR) {
            runs.findByPeriodYearAndPeriodMonthAndJurisdiction(
                    req.periodYear(), req.periodMonth(), jurisdiction)
                    .ifPresent(existing -> {
                        throw new BadRequestException(
                                "Payroll run already exists for " + req.periodYear()
                                        + "/" + req.periodMonth() + " (" + jurisdiction + ")");
                    });
        }

        PayrollRun r = new PayrollRun();
        r.setRunNo(BusinessNumbers.format("PR", 5, runs.nextRunNoSequence()));
        r.setPeriodYear(req.periodYear());
        r.setPeriodMonth(req.periodMonth());
        r.setJurisdiction(jurisdiction);
        r.setCurrency(req.currency() == null ? "AZN" : req.currency());
        r.setRunType(runType);
        r.setDescription(req.description());
        r.setEmployeeIds(req.employeeIds());
        r.setStatus(PayrollRunStatus.DRAFT);
        r.setCreatedBy(currentRequest.username());
        PayrollRun saved = runs.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null,
                Map.of("period", req.periodYear() + "-" + req.periodMonth(),
                       "runType", runType.name()));
        return saved;
    }

    @Transactional
    public PayrollRun calculate(UUID id) {
        PayrollRun r = get(id);
        if (r.getStatus() == PayrollRunStatus.PAID || r.getStatus() == PayrollRunStatus.CLOSED) {
            throw new BadRequestException(
                    "Cannot recalculate a " + r.getStatus() + " run. Reopen it first.");
        }
        PayrollRun calculated = engine.calculate(r);
        calculated.setStatus(PayrollRunStatus.CALCULATED);
        calculated.setCalculatedAt(OffsetDateTime.now());
        calculated.setCalculatedBy(currentRequest.username());
        PayrollRun saved = runs.save(calculated);
        // M331: build attendance impact summary for pre-approval review
        attendanceSummaryService.compute(saved.getId());
        audit.record(MODULE, ENTITY, id.toString(),
                "CALCULATE", null,
                Map.of("totalGross", saved.getTotalGross().toPlainString(),
                        "totalNet", saved.getTotalNet().toPlainString(),
                        "employees", saved.getEmployeeCount()));
        return saved;
    }

    @Transactional
    public PayrollBonus addBonus(UUID runId, AddBonusRequest req) {
        PayrollRun r = get(runId);
        if (r.getStatus() == PayrollRunStatus.PAID || r.getStatus() == PayrollRunStatus.CLOSED) {
            throw new BadRequestException("Cannot add bonuses to a " + r.getStatus() + " run");
        }
        PayrollBonus b = new PayrollBonus();
        b.setRunId(runId);
        b.setEmployeeId(req.employeeId());
        b.setBonusType(req.bonusType());
        b.setAmount(req.amount());
        b.setNote(req.note());
        b.setCreatedBy(currentRequest.username());
        PayrollBonus saved = bonuses.save(b);
        audit.record(MODULE, "PayrollBonus", saved.getId().toString(),
                "ADD", null,
                Map.of("runId", runId.toString(),
                        "employeeId", req.employeeId().toString(),
                        "amount", req.amount().toPlainString(),
                        "type", req.bonusType().name()));
        return saved;
    }

    @Transactional
    public PayrollRun submit(UUID id) {
        PayrollRun r = get(id);
        if (r.getStatus() != PayrollRunStatus.CALCULATED
                && r.getStatus() != PayrollRunStatus.REOPENED) {
            throw new BadRequestException(
                    "Only CALCULATED or REOPENED runs can be submitted (current: " + r.getStatus() + ")");
        }
        r.setStatus(PayrollRunStatus.UNDER_REVIEW);
        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                r.getId().toString(),
                r.getRunNo() + " — " + r.getPeriodYear() + "/" + String.format("%02d", r.getPeriodMonth())
                        + " — gross " + r.getTotalGross() + " " + r.getCurrency()
                        + ", net " + r.getTotalNet() + " " + r.getCurrency(),
                Map.of(
                        "period", r.getPeriodYear() + "-" + r.getPeriodMonth(),
                        "totalGross", r.getTotalGross().toPlainString(),
                        "totalNet", r.getTotalNet().toPlainString(),
                        "employees", r.getEmployeeCount())));
        r.setWorkflowInstanceId(instance.getId());
        PayrollRun saved = runs.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "SUBMIT", null, Map.of("period", r.getPeriodYear() + "-" + r.getPeriodMonth()));
        return saved;
    }

    @Transactional
    public PayrollRun onApproved(UUID id, String comment) {
        PayrollRun r = get(id);
        if (r.getStatus() != PayrollRunStatus.UNDER_REVIEW) return r;
        r.setStatus(PayrollRunStatus.APPROVED);
        r.setApprovedAt(OffsetDateTime.now());
        r.setApprovedBy(currentRequest.username());
        PayrollRun saved = runs.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public PayrollRun onRejected(UUID id, String comment) {
        PayrollRun r = get(id);
        if (r.getStatus() != PayrollRunStatus.UNDER_REVIEW) return r;
        r.setStatus(PayrollRunStatus.CALCULATED);
        PayrollRun saved = runs.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public PayrollRun onCancelled(UUID id, String comment) {
        PayrollRun r = get(id);
        if (r.getStatus() != PayrollRunStatus.UNDER_REVIEW) return r;
        r.setStatus(PayrollRunStatus.CALCULATED);
        PayrollRun saved = runs.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "CANCELLED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public PayrollRun markPaid(UUID id) {
        PayrollRun r = get(id);
        if (r.getStatus() != PayrollRunStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED runs can be marked paid");
        }
        r.setStatus(PayrollRunStatus.PAID);
        r.setPaidAt(OffsetDateTime.now());
        r.setPaidBy(currentRequest.username());
        PayrollRun saved = runs.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "PAID", null,
                Map.of("paidAt", saved.getPaidAt().toString()));
        eventPublisher.publishEvent(new PayrollRunPaidEvent(
                saved.getId(), saved.getRunNo(),
                saved.getPeriodYear(), saved.getPeriodMonth()));
        return saved;
    }

    @Transactional
    public PayrollRun close(UUID id) {
        PayrollRun r = get(id);
        if (r.getStatus() != PayrollRunStatus.PAID) {
            throw new BadRequestException("Only PAID runs can be closed");
        }
        r.setStatus(PayrollRunStatus.CLOSED);
        r.setClosedAt(OffsetDateTime.now());
        PayrollRun saved = runs.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "CLOSE", null, Map.of());
        return saved;
    }

    @Transactional
    public PayrollRun reopen(UUID id, String reason) {
        PayrollRun r = get(id);
        if (r.getStatus() != PayrollRunStatus.CLOSED && r.getStatus() != PayrollRunStatus.APPROVED) {
            throw new BadRequestException("Only CLOSED or APPROVED runs can be reopened");
        }
        r.setStatus(PayrollRunStatus.REOPENED);
        PayrollRun saved = runs.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "REOPEN", null,
                Map.of("reason", reason == null ? "" : reason));
        return saved;
    }
}
