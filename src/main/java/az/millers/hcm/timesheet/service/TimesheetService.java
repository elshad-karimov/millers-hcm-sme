package az.millers.hcm.timesheet.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.permission.domain.PermissionRequest;
import az.millers.hcm.permission.repo.PermissionRequestRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.timesheet.api.dto.DayCorrectionRequest;
import az.millers.hcm.timesheet.api.dto.TimesheetDayResponse;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetDay;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.repo.TimesheetDayRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

@Service
public class TimesheetService {

    public static final String WORKFLOW_DEFINITION = "TIMESHEET_APPROVAL";
    private static final String MODULE = "TIMESHEET";
    private static final String ENTITY = "Timesheet";

    private final TimesheetRepository timesheets;
    private final TimesheetDayRepository days;
    private final TimesheetGenerator generator;
    private final EmployeeRepository employees;
    private final PermissionRequestRepository permissions;
    private final WorkflowService workflowService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;

    public TimesheetService(TimesheetRepository timesheets,
                             TimesheetDayRepository days,
                             TimesheetGenerator generator,
                             EmployeeRepository employees,
                             PermissionRequestRepository permissions,
                             WorkflowService workflowService,
                             AuditService audit,
                             CurrentRequest currentRequest,
                             AccessScopeService accessScope) {
        this.timesheets = timesheets;
        this.days = days;
        this.generator = generator;
        this.employees = employees;
        this.permissions = permissions;
        this.workflowService = workflowService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
    }

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public Timesheet get(UUID id) {
        Timesheet t = timesheets.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found: " + id));
        // ABAC: hide rows the caller isn't scoped to behind a 404 (PRD 14.9).
        if (!accessScope.isAccessible(t.getEmployeeId())) {
            throw new ResourceNotFoundException("Timesheet not found: " + id);
        }
        return t;
    }

    @Transactional(readOnly = true)
    public List<Timesheet> listForPeriod(int year, int month) {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope == null) {
            return timesheets.findByPeriodYearAndPeriodMonthOrderByEmployeeIdAsc(year, month);
        }
        if (scope.isEmpty()) return List.of();
        return timesheets.findByPeriodYearAndPeriodMonthAndEmployeeIdInOrderByEmployeeIdAsc(
                year, month, scope);
    }

    @Transactional(readOnly = true)
    public List<TimesheetDay> daysOf(UUID timesheetId) {
        return days.findByTimesheetIdOrderByWorkDateAsc(timesheetId);
    }

    // ---------- Generation ----------

    @Transactional
    public Timesheet generate(UUID employeeId, int year, int month) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        Timesheet ts = timesheets.findByEmployeeIdAndPeriodYearAndPeriodMonth(employeeId, year, month)
                .orElseGet(() -> {
                    Timesheet n = new Timesheet();
                    n.setEmployeeId(employeeId);
                    n.setPeriodYear(year);
                    n.setPeriodMonth(month);
                    n.setStatus(TimesheetStatus.DRAFT);
                    n.setGeneratedBy(currentRequest.username());
                    return timesheets.save(n);
                });
        if (ts.getStatus() == TimesheetStatus.LOCKED || ts.getStatus() == TimesheetStatus.APPROVED) {
            throw new BadRequestException(
                    "Cannot regenerate a " + ts.getStatus() + " timesheet. Reopen it first.");
        }

        days.deleteByTimesheetId(ts.getId());
        days.flush();
        List<TimesheetDay> generated = generator.buildDays(employeeId, year, month);
        for (TimesheetDay d : generated) {
            d.setTimesheetId(ts.getId());
        }
        days.saveAll(generated);

        List<PermissionRequest> approvedPermissions = permissions.findApprovedInRange(
                employeeId, YearMonth.of(year, month).atDay(1),
                YearMonth.of(year, month).atEndOfMonth());
        recalculateTotals(ts, generated, approvedPermissions);
        ts.setStatus(TimesheetStatus.DRAFT);
        ts.setGeneratedAt(OffsetDateTime.now());
        ts.setGeneratedBy(currentRequest.username());
        Timesheet saved = timesheets.save(ts);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "GENERATE", null,
                Map.of("period", year + "-" + month,
                        "days", generated.size()));
        return saved;
    }

    // ---------- Submit / workflow callbacks ----------

    @Transactional
    public Timesheet submit(UUID id) {
        Timesheet ts = get(id);
        if (ts.getStatus() != TimesheetStatus.DRAFT && ts.getStatus() != TimesheetStatus.REOPENED) {
            throw new BadRequestException(
                    "Only DRAFT or REOPENED timesheets can be submitted (current: " + ts.getStatus() + ")");
        }
        ts.setStatus(TimesheetStatus.SUBMITTED);
        ts.setSubmittedAt(OffsetDateTime.now());
        ts.setSubmittedBy(currentRequest.username());

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                ts.getId().toString(),
                "Timesheet — " + ts.getPeriodYear() + "/"
                        + String.format("%02d", ts.getPeriodMonth())
                        + " — worked " + ts.getTotalWorkedHours() + "h"
                        + (ts.getTotalOvertimeHours().signum() > 0
                                ? ", OT " + ts.getTotalOvertimeHours() + "h"
                                : ""),
                Map.of(
                        "year", ts.getPeriodYear(),
                        "month", ts.getPeriodMonth(),
                        "workedHours", ts.getTotalWorkedHours().toPlainString(),
                        "overtimeHours", ts.getTotalOvertimeHours().toPlainString())));
        ts.setWorkflowInstanceId(instance.getId());

        Timesheet saved = timesheets.save(ts);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "SUBMIT", null,
                Map.of("year", ts.getPeriodYear(), "month", ts.getPeriodMonth()));
        return saved;
    }

    @Transactional
    public Timesheet onApproved(UUID id, String comment) {
        Timesheet ts = get(id);
        if (ts.getStatus() != TimesheetStatus.SUBMITTED) return ts;
        ts.setStatus(TimesheetStatus.APPROVED);
        ts.setApprovedAt(OffsetDateTime.now());
        ts.setApprovedBy(currentRequest.username());
        Timesheet saved = timesheets.save(ts);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public Timesheet onRejected(UUID id, String comment) {
        Timesheet ts = get(id);
        if (ts.getStatus() != TimesheetStatus.SUBMITTED) return ts;
        // Reject sends the timesheet back to DRAFT so HR can fix and resubmit.
        ts.setStatus(TimesheetStatus.DRAFT);
        Timesheet saved = timesheets.save(ts);
        audit.record(MODULE, ENTITY, id.toString(), "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public Timesheet onCancelled(UUID id, String comment) {
        Timesheet ts = get(id);
        if (ts.getStatus() != TimesheetStatus.SUBMITTED) return ts;
        ts.setStatus(TimesheetStatus.DRAFT);
        Timesheet saved = timesheets.save(ts);
        audit.record(MODULE, ENTITY, id.toString(), "CANCELLED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    // ---------- Manual day correction ----------

    @Transactional
    public TimesheetDay correctDay(UUID timesheetId, UUID dayId, DayCorrectionRequest req) {
        Timesheet ts = get(timesheetId);
        if (ts.getStatus() == TimesheetStatus.LOCKED) {
            throw new BadRequestException("Cannot correct a LOCKED timesheet. Reopen it first.");
        }
        TimesheetDay day = days.findById(dayId)
                .orElseThrow(() -> new ResourceNotFoundException("Day not found: " + dayId));
        if (!day.getTimesheetId().equals(timesheetId)) {
            throw new BadRequestException("Day does not belong to this timesheet");
        }
        TimesheetDayResponse before = TimesheetDayResponse.from(day);
        if (req.primaryCode() != null) day.setPrimaryCode(req.primaryCode());
        if (req.workedHours() != null) day.setWorkedHours(req.workedHours());
        if (req.overtimeHours() != null) day.setOvertimeHours(req.overtimeHours());
        day.setCorrectionReason(req.reason());
        day.setCorrectedBy(currentRequest.username());
        day.setCorrectedAt(OffsetDateTime.now());
        TimesheetDay saved = days.save(day);

        List<TimesheetDay> all = days.findByTimesheetIdOrderByWorkDateAsc(timesheetId);
        List<PermissionRequest> approvedPermissions = permissions.findApprovedInRange(
                ts.getEmployeeId(),
                YearMonth.of(ts.getPeriodYear(), ts.getPeriodMonth()).atDay(1),
                YearMonth.of(ts.getPeriodYear(), ts.getPeriodMonth()).atEndOfMonth());
        recalculateTotals(ts, all, approvedPermissions);
        timesheets.save(ts);

        audit.record(MODULE, "TimesheetDay", dayId.toString(),
                "MANUAL_CORRECTION", before, TimesheetDayResponse.from(saved));
        return saved;
    }

    // ---------- Internals ----------

    private void recalculateTotals(Timesheet ts, List<TimesheetDay> rows,
                                    List<PermissionRequest> permissionsApproved) {
        BigDecimal worked = BigDecimal.ZERO;
        BigDecimal overtime = BigDecimal.ZERO;
        BigDecimal permissionHours = BigDecimal.ZERO;
        int leaveDays = 0, sickDays = 0, btDays = 0, absentDays = 0;
        for (TimesheetDay d : rows) {
            worked = worked.add(d.getWorkedHours());
            overtime = overtime.add(d.getOvertimeHours());
            switch (d.getPrimaryCode()) {
                case "L"  -> leaveDays++;
                case "S"  -> sickDays++;
                case "BT" -> btDays++;
                case "A"  -> absentDays++;
                case "P"  -> permissionHours = permissionHours.add(
                        generator.permissionHoursForDay(d, permissionsApproved));
                default -> {}
            }
        }
        ts.setTotalWorkedHours(worked);
        ts.setTotalOvertimeHours(overtime);
        ts.setTotalLeaveDays(BigDecimal.valueOf(leaveDays));
        ts.setTotalSickDays(BigDecimal.valueOf(sickDays));
        ts.setTotalBtDays(BigDecimal.valueOf(btDays));
        ts.setTotalPermissionHrs(permissionHours);
        ts.setTotalAbsentDays(BigDecimal.valueOf(absentDays));
    }

    /** Reopen a previously LOCKED timesheet (Payroll workflow plugs into this). */
    @Transactional
    public Timesheet reopen(UUID id, String reason) {
        Timesheet ts = get(id);
        if (ts.getStatus() != TimesheetStatus.LOCKED && ts.getStatus() != TimesheetStatus.APPROVED) {
            throw new BadRequestException("Only LOCKED or APPROVED timesheets can be reopened");
        }
        ts.setStatus(TimesheetStatus.REOPENED);
        ts.setLockedAt(null);
        Timesheet saved = timesheets.save(ts);
        audit.record(MODULE, ENTITY, id.toString(), "REOPEN", null,
                Map.of("reason", reason == null ? "" : reason));
        return saved;
    }
}
