package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.service.HolidayService;
import az.millers.hcm.leave.api.dto.LeaveRequestResponse;
import az.millers.hcm.leave.api.dto.LeaveSubmitRequest;
import az.millers.hcm.leave.domain.BlackoutSeverity;
import az.millers.hcm.leave.domain.BlackoutWindow;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.domain.LeaveRequestStatus;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.domain.LeaveUnit;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;
import az.millers.hcm.common.BusinessNumbers;

@Service
public class LeaveRequestService {

    public static final String WORKFLOW_DEFINITION = "LEAVE_REQUEST_APPROVAL";
    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "LeaveRequest";

    private final LeaveRequestRepository requests;
    private final LeaveTypeRepository types;
    private final EmployeeRepository employees;
    private final LeaveBalanceService balances;
    private final WorkflowService workflowService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;
    private final HolidayService holidays;
    /** M123 — blackout windows + applicability checker. */
    private final BlackoutWindowService blackouts;
    /** M342 — period locks that freeze a date range for all mutations. */
    private final LeavePeriodLockService periodLocks;

    public LeaveRequestService(LeaveRequestRepository requests,
                                LeaveTypeRepository types,
                                EmployeeRepository employees,
                                LeaveBalanceService balances,
                                WorkflowService workflowService,
                                AuditService audit,
                                CurrentRequest currentRequest,
                                AccessScopeService accessScope,
                                HolidayService holidays,
                                BlackoutWindowService blackouts,
                                LeavePeriodLockService periodLocks) {
        this.requests = requests;
        this.types = types;
        this.employees = employees;
        this.balances = balances;
        this.workflowService = workflowService;
        this.audit = audit;
        this.blackouts = blackouts;
        this.periodLocks = periodLocks;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
        this.holidays = holidays;
    }

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public LeaveRequest get(UUID id) {
        LeaveRequest r = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + id));
        // ABAC: surface as 404 (not 403) when out of scope so we don't leak
        // the row's existence to a caller who shouldn't see it (PRD 14.9).
        if (!accessScope.isAccessible(r.getEmployeeId())) {
            throw new ResourceNotFoundException("Leave request not found: " + id);
        }
        return r;
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequest> list(UUID employeeId, LeaveRequestStatus status, Pageable pageable) {
        // ABAC scope (PRD 14.9). If the caller is restricted, narrow to the
        // intersection of the caller's allowed ids and the requested filter.
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope == null) {
            // Unrestricted (HR / admin / auditor).
            if (employeeId != null) {
                return requests.findByEmployeeIdOrderByStartDateDesc(employeeId, pageable);
            }
            if (status != null) {
                return requests.findByStatusOrderByStartDateDesc(status, pageable);
            }
            return requests.findAllByOrderByStartDateDesc(pageable);
        }
        if (scope.isEmpty()) return Page.empty(pageable);
        if (employeeId != null) {
            if (!scope.contains(employeeId)) return Page.empty(pageable);
            // A specific employee was requested AND it's in the caller's scope —
            // the legacy path is the cheapest way to honour it.
            return requests.findByEmployeeIdOrderByStartDateDesc(employeeId, pageable);
        }
        if (status != null) {
            return requests.findByEmployeeIdInAndStatusOrderByStartDateDesc(scope, status, pageable);
        }
        return requests.findByEmployeeIdInOrderByStartDateDesc(scope, pageable);
    }

    // ---------- Submit ----------

    @Transactional
    public LeaveRequest submit(LeaveSubmitRequest req) {
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        LeaveType type = types.findById(req.leaveTypeId())
                .orElseThrow(() -> new BadRequestException("Leave type not found: " + req.leaveTypeId()));
        if (!type.isActive()) {
            throw new BadRequestException("Leave type is inactive: " + type.getCode());
        }
        if (req.startDate().isAfter(req.endDate())) {
            throw new BadRequestException("startDate must be on or before endDate");
        }

        LeaveUnit unit = type.getLeaveUnit() != null ? type.getLeaveUnit() : LeaveUnit.DAYS;
        boolean halfDay;
        BigDecimal totalDays;
        BigDecimal durationHours = null;
        LocalTime startTime = null;
        LocalTime endTime = null;

        if (unit == LeaveUnit.HOURS) {
            // Time-based request — must be a single day with both times provided
            if (!req.startDate().equals(req.endDate())) {
                throw new BadRequestException("HOURS-unit leave types require startDate == endDate");
            }
            if (req.startTime() == null || req.endTime() == null) {
                throw new BadRequestException("HOURS-unit leave types require startTime and endTime");
            }
            if (!req.startTime().isBefore(req.endTime())) {
                throw new BadRequestException("startTime must be before endTime");
            }
            startTime = req.startTime();
            endTime = req.endTime();
            long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
            durationHours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            BigDecimal hpd = type.getHoursPerDay() != null ? type.getHoursPerDay() : BigDecimal.valueOf(8);
            totalDays = durationHours.divide(hpd, 4, RoundingMode.HALF_UP);
            halfDay = false;
        } else if (unit == LeaveUnit.HALF_DAY) {
            // This type forces half-day only
            if (!req.startDate().equals(req.endDate())) {
                throw new BadRequestException("HALF_DAY-unit leave types must be a single date");
            }
            halfDay = true;
            totalDays = new BigDecimal("0.5");
        } else {
            // Standard DAYS-unit
            halfDay = Boolean.TRUE.equals(req.halfDay());
            if (halfDay && !req.startDate().equals(req.endDate())) {
                throw new BadRequestException("Half-day requests must be a single date");
            }
            // M38: when the type sets exclude_holidays, pre-fetch the
            // overlap month(s) of holidays once and subtract them in the
            // day-count loop. Otherwise pass an empty set so the helper is
            // a pure date arithmetic.
            Set<LocalDate> holidaySet = type.isExcludeHolidays()
                    ? holidays.holidayDatesBetween(req.startDate(), req.endDate())
                    : Set.of();
            totalDays = computeDays(req.startDate(), req.endDate(), halfDay,
                    type.isExcludeWeekends(), holidaySet);
        }

        if (totalDays.signum() <= 0) {
            throw new BadRequestException(
                    "Computed leave duration is zero (likely all weekends/holidays excluded)");
        }
        if (type.getMaxConsecutiveDays() != null
                && totalDays.compareTo(BigDecimal.valueOf(type.getMaxConsecutiveDays())) > 0) {
            throw new BadRequestException(
                    "Request exceeds maxConsecutiveDays for " + type.getCode() + ": "
                            + type.getMaxConsecutiveDays());
        }
        if (type.isRequiresReplacement() && req.replacementEmployeeId() == null) {
            throw new BadRequestException(
                    "This leave type requires a replacement employee");
        }
        if (type.isRequiresAttachment()
                && (req.attachmentUrl() == null || req.attachmentUrl().isBlank())) {
            throw new BadRequestException("This leave type requires an attachment");
        }

        // M123 — blackout window gate. BLOCK windows hard-reject; the
        // BadRequestException carries a multi-line message naming each
        // conflicting window so the user sees what's in the way.
        // REQUIRES_APPROVAL windows let the request through but raise
        // M342 — period lock blocks new submissions overlapping locked window
        var activeLocks = periodLocks.activeLocksFor(req.startDate(), req.endDate(), req.leaveTypeId());
        if (!activeLocks.isEmpty()) {
            az.millers.hcm.leave.domain.LeavePeriodLock lock = activeLocks.get(0);
            throw new BadRequestException(
                    "Leave period is locked from " + lock.getPeriodStart()
                    + " to " + lock.getPeriodEnd()
                    + (lock.getReason() != null ? ": " + lock.getReason() : ""));
        }

        // blackoutFlag so HR sees it in their approval inbox.
        java.util.List<BlackoutWindow> hits = blackouts.applicableFor(
                req.employeeId(), req.leaveTypeId(), req.startDate(), req.endDate());
        BlackoutSeverity worst = BlackoutChecker.worstSeverity(hits);
        if (worst == BlackoutSeverity.BLOCK) {
            throw new BadRequestException(BlackoutChecker.formatBlockMessage(hits));
        }
        boolean blackoutFlag = worst == BlackoutSeverity.REQUIRES_APPROVAL;

        LeaveRequest r = new LeaveRequest();
        r.setRequestNo(BusinessNumbers.format("LR", 5, requests.nextRequestNoSequence()));
        r.setEmployeeId(req.employeeId());
        r.setLeaveTypeId(req.leaveTypeId());
        r.setStartDate(req.startDate());
        r.setEndDate(req.endDate());
        r.setHalfDay(halfDay);
        r.setTotalDays(totalDays);
        r.setStartTime(startTime);
        r.setEndTime(endTime);
        r.setDurationHours(durationHours);
        r.setReason(req.reason());
        r.setAttachmentUrl(req.attachmentUrl());
        r.setReplacementEmployeeId(req.replacementEmployeeId());
        r.setStatus(LeaveRequestStatus.PENDING);
        r.setBlackoutFlag(blackoutFlag);
        r.setCreatedBy(currentRequest.username());
        LeaveRequest saved = requests.save(r);

        balances.reserve(req.employeeId(), req.leaveTypeId(),
                req.startDate().getYear(), totalDays, type);

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                saved.getId().toString(),
                type.getName() + " — " + saved.getRequestNo() + " (" + totalDays + " day"
                        + (totalDays.compareTo(BigDecimal.ONE) == 0 ? "" : "s") + ")",
                Map.of(
                        "leaveType", type.getCode(),
                        "totalDays", totalDays.toPlainString(),
                        "startDate", saved.getStartDate().toString(),
                        "endDate", saved.getEndDate().toString())));
        saved.setWorkflowInstanceId(instance.getId());
        saved = requests.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "SUBMIT", null, LeaveRequestResponse.from(saved));
        return saved;
    }

    // ---------- Workflow callbacks ----------

    @Transactional
    public LeaveRequest onApproved(UUID requestId, String comment) {
        LeaveRequest r = get(requestId);
        if (r.getStatus() != LeaveRequestStatus.PENDING) return r;
        balances.commit(r.getEmployeeId(), r.getLeaveTypeId(),
                r.getStartDate().getYear(), r.getTotalDays());
        r.setStatus(LeaveRequestStatus.APPROVED);
        LeaveRequest saved = requests.save(r);
        audit.record(MODULE, ENTITY, requestId.toString(),
                "APPROVED", null,
                Map.of("comment", comment == null ? "" : comment,
                        "totalDays", r.getTotalDays().toPlainString()));
        return saved;
    }

    @Transactional
    public LeaveRequest onRejected(UUID requestId, String comment) {
        LeaveRequest r = get(requestId);
        if (r.getStatus() != LeaveRequestStatus.PENDING) return r;
        balances.release(r.getEmployeeId(), r.getLeaveTypeId(),
                r.getStartDate().getYear(), r.getTotalDays());
        r.setStatus(LeaveRequestStatus.REJECTED);
        LeaveRequest saved = requests.save(r);
        audit.record(MODULE, ENTITY, requestId.toString(),
                "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public LeaveRequest onCancelled(UUID requestId, String comment) {
        LeaveRequest r = get(requestId);
        if (r.getStatus() != LeaveRequestStatus.PENDING) return r;
        // M342 — period lock blocks cancellations too (balance would change)
        var activeLocks = periodLocks.activeLocksFor(r.getStartDate(), r.getEndDate(), r.getLeaveTypeId());
        if (!activeLocks.isEmpty()) {
            az.millers.hcm.leave.domain.LeavePeriodLock lock = activeLocks.get(0);
            throw new BadRequestException(
                    "Cannot cancel: leave period is locked from " + lock.getPeriodStart()
                    + " to " + lock.getPeriodEnd()
                    + (lock.getReason() != null ? ": " + lock.getReason() : ""));
        }
        balances.release(r.getEmployeeId(), r.getLeaveTypeId(),
                r.getStartDate().getYear(), r.getTotalDays());
        r.setStatus(LeaveRequestStatus.CANCELLED);
        LeaveRequest saved = requests.save(r);
        audit.record(MODULE, ENTITY, requestId.toString(),
                "CANCELLED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    // ---------- Helpers ----------

    /**
     * Counts billable leave days in {@code [start, end]} inclusive.
     *
     * <p>M38 added the {@code holidayDates} parameter — when the leave
     * type sets {@code exclude_holidays}, the caller pre-fetches the
     * matching dates once via {@link HolidayService#holidayDatesBetween}
     * and we skip them here. Pre-M38 callers can pass {@link Set#of()}
     * for back-compat.
     */
    static BigDecimal computeDays(LocalDate start, LocalDate end, boolean halfDay,
                                   boolean excludeWeekends,
                                   Set<LocalDate> holidayDates) {
        if (halfDay) return new BigDecimal("0.5");
        int count = 0;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean isHoliday = holidayDates.contains(d);
            if ((!excludeWeekends || !isWeekend) && !isHoliday) {
                count++;
            }
            d = d.plusDays(1);
        }
        return BigDecimal.valueOf(count);
    }
}
