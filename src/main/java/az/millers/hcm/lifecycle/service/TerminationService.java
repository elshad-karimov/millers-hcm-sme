package az.millers.hcm.lifecycle.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
import az.millers.hcm.corehr.service.EmployeeHistoryService;
import az.millers.hcm.leave.domain.LeaveBalance;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.lifecycle.api.dto.ClearanceUpdateRequest;
import az.millers.hcm.lifecycle.event.TerminationProcessedEvent;
import az.millers.hcm.lifecycle.api.dto.ExitInterviewRequest;
import az.millers.hcm.lifecycle.api.dto.ExitInterviewResponse;
import az.millers.hcm.lifecycle.api.dto.TerminationResponse;
import az.millers.hcm.lifecycle.api.dto.TerminationSubmitRequest;
import az.millers.hcm.lifecycle.domain.ExitInterview;
import az.millers.hcm.lifecycle.domain.TerminationRequest;
import az.millers.hcm.lifecycle.domain.TerminationStatus;
import az.millers.hcm.lifecycle.repo.ExitInterviewRepository;
import az.millers.hcm.lifecycle.repo.TerminationRequestRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.service.FinalSettlementService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.service.StaffingService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * Termination service (PRD 8.11). Submits a termination request through
 * TERMINATION_APPROVAL; on approval the engine fires {@code onApproved},
 * the HR specialist completes clearances + exit interview, then
 * {@code process} flips the Employee to TERMINATED, frees the staffing
 * position and snapshots a final-settlement payout.
 */
@Service
public class TerminationService {

    public static final String WORKFLOW_DEFINITION = "TERMINATION_APPROVAL";
    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "TerminationRequest";
    private static final BigDecimal AVG_DAYS_PER_MONTH = new BigDecimal("21.67");

    private static final Logger log = LoggerFactory.getLogger(TerminationService.class);

    private final TerminationRequestRepository terminations;
    private final ExitInterviewRepository exitInterviews;
    private final EmployeeRepository employees;
    private final WorkflowService workflowService;
    private final StaffingService staffingService;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveBalanceRepository leaveBalances;
    private final EmployeeCompensationRepository compensations;
    private final FinalSettlementService finalSettlement;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final EmployeeHistoryService historyService;
    private final ApplicationEventPublisher eventPublisher;

    public TerminationService(TerminationRequestRepository terminations,
                              ExitInterviewRepository exitInterviews,
                              EmployeeRepository employees,
                              WorkflowService workflowService,
                              StaffingService staffingService,
                              LeaveTypeRepository leaveTypes,
                              LeaveBalanceRepository leaveBalances,
                              EmployeeCompensationRepository compensations,
                              FinalSettlementService finalSettlement,
                              AuditService audit,
                              CurrentRequest currentRequest,
                              EmployeeHistoryService historyService,
                              ApplicationEventPublisher eventPublisher) {
        this.terminations = terminations;
        this.exitInterviews = exitInterviews;
        this.employees = employees;
        this.workflowService = workflowService;
        this.staffingService = staffingService;
        this.leaveTypes = leaveTypes;
        this.leaveBalances = leaveBalances;
        this.compensations = compensations;
        this.finalSettlement = finalSettlement;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.historyService = historyService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public TerminationRequest get(UUID id) {
        return terminations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Termination not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<TerminationRequest> list(UUID employeeId, TerminationStatus status, Pageable pageable) {
        if (employeeId != null) {
            return terminations.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
        }
        if (status != null) {
            return terminations.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return terminations.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public TerminationRequest submit(TerminationSubmitRequest req) {
        Employee employee = employees.findById(req.employeeId())
                .orElseThrow(() -> new BadRequestException("Employee not found: " + req.employeeId()));
        if (employee.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new BadRequestException("Employee is already terminated");
        }
        if (req.lastWorkingDate().isBefore(req.noticeDate())) {
            throw new BadRequestException("lastWorkingDate cannot be before noticeDate");
        }
        if (req.effectiveDate().isBefore(req.lastWorkingDate())) {
            throw new BadRequestException("effectiveDate cannot be before lastWorkingDate");
        }

        TerminationRequest t = new TerminationRequest();
        t.setTerminationNo(String.format("TRM-%05d", terminations.nextNoSequence()));
        t.setEmployeeId(req.employeeId());
        t.setReasonCode(req.reasonCode());
        t.setReasonDetail(req.reasonDetail());
        t.setNoticeDate(req.noticeDate());
        t.setLastWorkingDate(req.lastWorkingDate());
        t.setEffectiveDate(req.effectiveDate());
        t.setAttachmentUrls(req.attachmentUrls());
        t.setNote(req.note());
        t.setStatus(TerminationStatus.PENDING);
        t.setCreatedBy(currentRequest.username());
        TerminationRequest saved = terminations.save(t);

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                saved.getId().toString(),
                saved.getTerminationNo() + " — " + employee.getFirstName() + " " + employee.getLastName()
                        + " (" + saved.getReasonCode() + ", effective " + saved.getEffectiveDate() + ")",
                Map.of(
                        "employeeNo", employee.getEmployeeNo(),
                        "employeeName", employee.getFirstName() + " " + employee.getLastName(),
                        "reasonCode", saved.getReasonCode().name(),
                        "effectiveDate", saved.getEffectiveDate().toString())));
        saved.setWorkflowInstanceId(instance.getId());
        saved = terminations.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "SUBMIT", null, TerminationResponse.from(saved));
        return saved;
    }

    @Transactional
    public TerminationRequest updateClearance(UUID id, ClearanceUpdateRequest req) {
        TerminationRequest t = get(id);
        if (t.getStatus() != TerminationStatus.APPROVED) {
            throw new BadRequestException(
                    "Clearance can only be updated on APPROVED terminations");
        }
        TerminationResponse before = TerminationResponse.from(t);
        if (req.clearanceIt() != null)      t.setClearanceIt(req.clearanceIt());
        if (req.clearanceHr() != null)      t.setClearanceHr(req.clearanceHr());
        if (req.clearanceFinance() != null) t.setClearanceFinance(req.clearanceFinance());
        if (req.clearanceAssets() != null)  t.setClearanceAssets(req.clearanceAssets());
        TerminationRequest saved = terminations.save(t);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE_CLEARANCE", before, TerminationResponse.from(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public ExitInterview getExitInterview(UUID terminationId) {
        get(terminationId); // verify termination exists
        return exitInterviews.findByTerminationId(terminationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No exit interview found for termination: " + terminationId));
    }

    @Transactional
    public ExitInterview recordExitInterview(UUID id, ExitInterviewRequest req) {
        TerminationRequest t = get(id);
        if (t.getStatus() != TerminationStatus.APPROVED && t.getStatus() != TerminationStatus.PROCESSED) {
            throw new BadRequestException(
                    "Exit interview can only be recorded after approval");
        }
        ExitInterview ei = exitInterviews.findByTerminationId(id).orElseGet(ExitInterview::new);
        boolean isNew = ei.getId() == null;
        ei.setTerminationId(id);
        if (isNew) {
            ei.setConductedAt(OffsetDateTime.now());
            ei.setConductedBy(currentRequest.username());
        }
        ei.setOverallRating(req.overallRating());
        ei.setWouldRecommend(req.wouldRecommend());
        ei.setReasonForLeaving(req.reasonForLeaving());
        ei.setFeedback(req.feedback());
        ei.setImprovementSuggestions(req.improvementSuggestions());
        ExitInterview saved = exitInterviews.save(ei);
        audit.record(MODULE, "ExitInterview", saved.getId().toString(),
                isNew ? "CREATE" : "UPDATE", null, ExitInterviewResponse.from(saved));
        return saved;
    }

    /**
     * Final processing of an APPROVED termination (PRD 8.11.5):
     * - flips Employee to TERMINATED
     * - frees the staffing position (occupied −1) if linked
     * - snapshots unused-leave payout and final settlement amount
     */
    @Transactional
    public TerminationRequest process(UUID id) {
        TerminationRequest t = get(id);
        if (t.getStatus() != TerminationStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED terminations can be processed");
        }
        Employee employee = employees.findById(t.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + t.getEmployeeId()));

        TerminationResponse before = TerminationResponse.from(t);
        Map<String, Object> calcDetails = new LinkedHashMap<>();

        // ---- Payout: unused-leave + (optional) severance ----
        EmployeeCompensation comp = compensations
                .findActiveOn(employee.getId(), t.getEffectiveDate())
                .orElse(null);
        BigDecimal monthlyBase = comp == null ? BigDecimal.ZERO : comp.getMonthlyBaseSalary();
        String currency = comp == null ? "AZN" : comp.getCurrency();
        BigDecimal dailyRate = monthlyBase.signum() > 0
                ? monthlyBase.divide(AVG_DAYS_PER_MONTH, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int year = t.getEffectiveDate().getYear();
        BigDecimal unusedAnnualDays = computeUnusedAnnualDays(employee.getId(), year, calcDetails);
        BigDecimal unusedLeavePayout = unusedAnnualDays.multiply(dailyRate)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal severance = computeSeverance(t, dailyRate, calcDetails);
        BigDecimal finalSettlement = unusedLeavePayout.add(severance).setScale(2, RoundingMode.HALF_UP);

        calcDetails.put("monthlyBase", monthlyBase.toPlainString());
        calcDetails.put("avgDaysPerMonth", AVG_DAYS_PER_MONTH.toPlainString());
        calcDetails.put("dailyRate", dailyRate.toPlainString());
        calcDetails.put("unusedAnnualDays", unusedAnnualDays.toPlainString());
        calcDetails.put("unusedLeavePayout", unusedLeavePayout.toPlainString());
        calcDetails.put("severance", severance.toPlainString());
        calcDetails.put("finalSettlement", finalSettlement.toPlainString());

        t.setUnusedLeaveDays(unusedAnnualDays);
        t.setUnusedLeavePayout(unusedLeavePayout);
        t.setSeveranceAmount(severance);
        t.setFinalSettlementAmount(finalSettlement);
        t.setCurrency(currency);
        t.setPayoutCalcDetails(calcDetails);

        // ---- Payroll integration: create FINAL_SETTLEMENT run (PRD §8.11.4) ----
        try {
            this.finalSettlement.createRun(
                    t.getId(),
                    employee.getId(),
                    t.getEffectiveDate(),
                    finalSettlement,
                    unusedLeavePayout,
                    severance,
                    currency,
                    "AZ",
                    t.getTerminationNo());
        } catch (Exception ex) {
            // Payroll run creation must not block the termination processing.
            log.warn("Failed to create final-settlement payroll run for {}: {}",
                    t.getTerminationNo(), ex.getMessage());
            calcDetails.put("payrollRunError", ex.getMessage());
            t.setPayoutCalcDetails(calcDetails);
        }

        // ---- Employee status flip ----
        EmploymentStatus oldStatus = employee.getEmploymentStatus();
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);
        employee.setUpdatedBy(currentRequest.username());
        Employee savedEmployee = employees.save(employee);

        // M62 / P1-11: open the TERMINATED status slice effective on the
        // termination's effective_date. Uses the shared EmployeeHistoryService
        // — same close-prior-row choreography as every other status transition.
        historyService.recordStatusSlice(
                savedEmployee.getId(),
                EmploymentStatus.TERMINATED,
                t.getEffectiveDate() != null ? t.getEffectiveDate() : java.time.LocalDate.now(),
                "Terminated — " + t.getReasonCode()
                        + (t.getReasonDetail() == null ? "" : " (" + t.getReasonDetail() + ")"),
                MODULE, ENTITY, t.getId().toString());

        // ---- Staffing: release the position ----
        if (savedEmployee.getPositionId() != null) {
            try {
                staffingService.adjustOccupancy(savedEmployee.getPositionId(), -1,
                        "Termination " + t.getTerminationNo());
            } catch (RuntimeException ex) {
                log.warn("Failed to release position {} on termination {}: {}",
                        savedEmployee.getPositionId(), t.getTerminationNo(), ex.getMessage());
                calcDetails.put("positionAdjustError", ex.getMessage());
                t.setPayoutCalcDetails(calcDetails);
            }
        }

        t.setStatus(TerminationStatus.PROCESSED);
        t.setProcessedAt(OffsetDateTime.now());
        t.setProcessedBy(currentRequest.username());
        TerminationRequest saved = terminations.save(t);

        audit.record(MODULE, ENTITY, id.toString(), "PROCESSED",
                before, TerminationResponse.from(saved));
        audit.record("CORE_HR", "Employee", employee.getId().toString(), "TERMINATE",
                Map.of("oldStatus", oldStatus == null ? "" : oldStatus.name()),
                Map.of("newStatus", "TERMINATED",
                        "terminationNo", saved.getTerminationNo(),
                        "effectiveDate", saved.getEffectiveDate().toString()));

        // Notify the manager, IT, and Finance (PRD §8.11.6 AC) — async so
        // a notification failure can never roll back this transaction.
        eventPublisher.publishEvent(new TerminationProcessedEvent(
                saved.getId(),
                saved.getTerminationNo(),
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getFirstName() + " " + employee.getLastName(),
                employee.getManagerId(),
                saved.getReasonCode(),
                saved.getEffectiveDate()));

        return saved;
    }

    // ---------- Workflow callbacks ----------

    @Transactional
    public TerminationRequest onApproved(UUID id, String comment) {
        TerminationRequest t = get(id);
        if (t.getStatus() != TerminationStatus.PENDING) return t;
        t.setStatus(TerminationStatus.APPROVED);
        TerminationRequest saved = terminations.save(t);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public TerminationRequest onRejected(UUID id, String comment) {
        TerminationRequest t = get(id);
        if (t.getStatus() != TerminationStatus.PENDING) return t;
        t.setStatus(TerminationStatus.REJECTED);
        TerminationRequest saved = terminations.save(t);
        audit.record(MODULE, ENTITY, id.toString(), "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public TerminationRequest onCancelled(UUID id, String comment) {
        TerminationRequest t = get(id);
        if (t.getStatus() != TerminationStatus.PENDING) return t;
        t.setStatus(TerminationStatus.CANCELLED);
        TerminationRequest saved = terminations.save(t);
        audit.record(MODULE, ENTITY, id.toString(), "CANCELLED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    // ---------- Internals ----------

    private BigDecimal computeUnusedAnnualDays(UUID employeeId, int year, Map<String, Object> details) {
        LeaveType annual = leaveTypes.findByCode("ANNUAL").orElse(null);
        if (annual == null) {
            details.put("annualLeaveType", "not found");
            return BigDecimal.ZERO;
        }
        LeaveBalance balance = leaveBalances
                .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, annual.getId(), year)
                .orElse(null);
        if (balance == null) {
            details.put("annualBalance", "no row for year " + year);
            return BigDecimal.ZERO;
        }
        BigDecimal remaining = balance.remaining().max(BigDecimal.ZERO);
        Map<String, Object> bd = new HashMap<>();
        bd.put("entitlement", balance.getEntitlementDays().toPlainString());
        bd.put("carriedForward", balance.getCarriedForwardDays().toPlainString());
        bd.put("adjustment", balance.getAdjustmentDays().toPlainString());
        bd.put("used", balance.getUsedDays().toPlainString());
        bd.put("reserved", balance.getReservedDays().toPlainString());
        bd.put("remaining", remaining.toPlainString());
        details.put("annualBalance", bd);
        return remaining.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Severance approximation: REDUNDANCY → 1 month, INVOLUNTARY_DISMISSAL → 2 weeks.
     * Other reasons → 0. The PRD leaves this configurable; this is a working default
     * that the rule engine can replace later (PRD 8.11.5).
     */
    private BigDecimal computeSeverance(TerminationRequest t, BigDecimal dailyRate,
                                         Map<String, Object> details) {
        BigDecimal days = switch (t.getReasonCode()) {
            case REDUNDANCY -> AVG_DAYS_PER_MONTH;                       // ~1 month
            case INVOLUNTARY_DISMISSAL -> new BigDecimal("10");           // 2 working weeks
            default -> BigDecimal.ZERO;
        };
        details.put("severanceDays", days.toPlainString());
        return days.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP);
    }
}
