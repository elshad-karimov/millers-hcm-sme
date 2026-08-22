package az.millers.hcm.timesheet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.workflow.api.dto.ActionRequest;
import az.millers.hcm.workflow.domain.ActionType;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.service.WorkflowService;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ApproveRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.BulkApproveResult;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.CorrectionView;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.QueueRow;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.RejectRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ReturnRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ReviewDay;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ReviewView;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.FindingView;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.QuantityView;
import az.millers.hcm.timesheet.domain.DayApprovalState;
import az.millers.hcm.timesheet.domain.DayQuantity;
import az.millers.hcm.timesheet.domain.TimeCategory;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetDay;
import az.millers.hcm.timesheet.domain.TimesheetMonthTotal;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.repo.DayQuantityRepository;
import az.millers.hcm.timesheet.repo.TimeCategoryRepository;
import az.millers.hcm.timesheet.repo.TimesheetDayRepository;
import az.millers.hcm.timesheet.repo.TimesheetMonthTotalRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * Manager review and decision on submitted timesheets.
 *
 * <p>Two rules run through everything here.
 *
 * <p><strong>Hierarchy.</strong> Every query and every decision is narrowed to
 * the caller's own reports through {@link AccessScopeService}. An employee
 * outside the hierarchy is <em>absent</em> from the queue and 404s on direct
 * access — not 403 — so the queue never leaks the existence of people the
 * manager may not see.
 *
 * <p><strong>Nobody approves themselves.</strong> Checked explicitly rather
 * than left to hierarchy configuration, because a self-referencing manager row
 * is a data-entry mistake that would otherwise become a control failure.
 *
 * <p>Contains no monetary logic.
 */
@Service
public class TimesheetApprovalService {

    private static final String MODULE = "TIMESHEET";
    private static final String ENTITY = "Timesheet";
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    /** Months an approver can still act on, at either stage of the chain. */
    private static final Set<TimesheetStatus> ACTIONABLE =
            Set.of(TimesheetStatus.SUBMITTED, TimesheetStatus.PENDING_HR, TimesheetStatus.RETURNED);

    /** Roles that see the HR stage of the chain in their queue. */
    private static final Set<String> HR_ROLES = Set.of(
            SecurityRoles.R_HR_ADMIN, SecurityRoles.R_HR_SPECIALIST, SecurityRoles.R_SYSTEM_ADMIN);

    /** Owns the approval route; this service only records decisions. */
    private final WorkflowService workflowService;
    private final TimesheetRepository timesheets;
    private final TimesheetDayRepository days;
    private final DayQuantityRepository quantities;
    private final TimesheetMonthTotalRepository monthTotals;
    private final TimeCategoryRepository categories;
    private final EmployeeRepository employees;
    private final DailySummaryRepository summaries;
    /** Resolves the project a day is booked to, for the review screen. */
    private final az.millers.hcm.timesheet.repo.TimesheetProjectRepository projects;
    private final TimesheetPeriodService periods;
    private final TimesheetCorrectionService corrections;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    /**
     * Runs one unit of work in its own transaction.
     *
     * <p>A TransactionTemplate rather than {@code @Transactional} on a second
     * method: that annotation only takes effect through the bean's proxy, and
     * {@code this.method()} does not go through it — the propagation would be
     * silently inert, which is exactly the bug being fixed. Routing around that
     * needs a self-reference, and a self-reference risks the application
     * failing to start; this project has no context test that would catch it
     * before a deploy. The template has no such failure mode.
     */
    private final TransactionTemplate inOwnTransaction;

    public TimesheetApprovalService(WorkflowService workflowService,
                                   TimesheetRepository timesheets,
                                    TimesheetDayRepository days,
                                    DayQuantityRepository quantities,
                                    TimesheetMonthTotalRepository monthTotals,
                                    TimeCategoryRepository categories,
                                    EmployeeRepository employees,
                                    DailySummaryRepository summaries,
                                    az.millers.hcm.timesheet.repo.TimesheetProjectRepository projects,
                                    TimesheetPeriodService periods,
                                    TimesheetCorrectionService corrections,
                                    AccessScopeService accessScope,
                                    AuditService audit,
                                    CurrentRequest currentRequest,
                                    PlatformTransactionManager transactionManager) {
        this.inOwnTransaction = new TransactionTemplate(transactionManager);
        this.inOwnTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.workflowService = workflowService;
        this.timesheets = timesheets;
        this.days = days;
        this.quantities = quantities;
        this.monthTotals = monthTotals;
        this.categories = categories;
        this.employees = employees;
        this.summaries = summaries;
        this.projects = projects;
        this.periods = periods;
        this.corrections = corrections;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ---------- Queue ----------

    /** Timesheets in the caller's hierarchy for a period, newest submissions first. */
    @Transactional(readOnly = true)
    public List<QueueRow> queue(int year, int month, TimesheetStatus status) {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        Set<UUID> nominated = nominatedEmployeeIds();
        Set<UUID> reports = reportingLineEmployeeIds();
        Set<TimesheetStatus> wanted = status == null ? myStageStatuses() : Set.of(status);

        List<Timesheet> found;
        if (scope == null) {
            found = timesheets.findByPeriodYearAndPeriodMonthAndStatusIn(year, month, wanted);
        } else {
            // M330 — a nominated timesheet approver is usually NOT in the
            // subject's reporting line, so the hierarchy scope alone would hand
            // them an empty queue and a 404 on the month they are appointed to
            // check. Union the two; the filter below keeps the nomination
            // narrow (their stage only, and timesheets only).
            Set<UUID> visible = new java.util.HashSet<>(scope);
            visible.addAll(nominated);
            visible.addAll(reports);
            if (visible.isEmpty()) return List.of();
            found = timesheets
                    .findByPeriodYearAndPeriodMonthAndStatusInAndEmployeeIdInOrderByEmployeeIdAsc(
                            year, month, wanted, visible);
        }

        UUID self = currentEmployeeIdOrNull();
        Map<UUID, Employee> employeeById = employeesOf(found);

        return found.stream()
                // A manager's own month never appears in their own queue.
                .filter(t -> self == null || !t.getEmployeeId().equals(self))
                // Someone visible ONLY by nomination sees that month once it
                // reaches them — not while it is still with the manager. Once
                // it has moved past SUBMITTED it stays visible, including after
                // approval: a person who signed a month can look back at what
                // they signed.
                .filter(t -> scope == null
                        || scope.contains(t.getEmployeeId())
                        // A direct report's month is this caller's to approve
                        // from the moment it is submitted — that IS their step.
                        || reports.contains(t.getEmployeeId())
                        || t.getStatus() != TimesheetStatus.SUBMITTED)
                .map(t -> toQueueRow(t, employeeById.get(t.getEmployeeId())))
                .toList();
    }

    /**
     * Which stage of the chain is waiting on the caller.
     *
     * <p>A manager's queue shows SUBMITTED (theirs to approve first); HR's shows
     * PENDING_HR (already manager-approved). Someone who is both — an HR admin
     * who also manages a team — sees both, which is correct rather than a
     * special case. RETURNED stays visible to whoever can act on it.
     */
    private Set<TimesheetStatus> myStageStatuses() {
        Set<TimesheetStatus> out = new java.util.HashSet<>();
        out.add(TimesheetStatus.RETURNED);
        if (HR_ROLES.stream().anyMatch(currentRequest::hasRole)
                // M330 — the second signature is the employee's NAMED timesheet
                // approver, who may hold no HR role at all.
                || !nominatedEmployeeIds().isEmpty()) {
            out.add(TimesheetStatus.PENDING_HR);
        }
        // Anyone who reaches this queue at all can act as an approver at stage 1
        // for the employees in their scope.
        out.add(TimesheetStatus.SUBMITTED);
        return out;
    }

    // ---------- Review ----------

    @Transactional(readOnly = true)
    public ReviewView review(UUID timesheetId) {
        Timesheet ts = accessible(timesheetId);
        Employee employee = employees.findById(ts.getEmployeeId()).orElse(null);

        Map<String, TimeCategory> catalog = catalogByCode();
        // Resolved once for the month rather than per day — a 31-row review
        // should not issue 31 project lookups.
        Map<java.util.UUID, String> projectNames = projects.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        az.millers.hcm.timesheet.domain.TimesheetProject::getId,
                        pr -> pr.getCode() + " — " + pr.getName(),
                        (a, b) -> a));
        List<TimesheetDay> allDays = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
        Map<UUID, List<DayQuantity>> byDay = quantitiesByDay(allDays);

        BigDecimal entered = BigDecimal.ZERO;
        BigDecimal attendance = BigDecimal.ZERO;
        List<ReviewDay> reviewDays = new ArrayList<>();

        for (TimesheetDay d : allDays) {
            List<DayQuantity> qs = byDay.getOrDefault(d.getId(), List.of());
            BigDecimal dayEntered = baseHours(qs, catalog);
            BigDecimal dayAttendance = attendanceHours(ts.getEmployeeId(), d.getWorkDate());

            entered = entered.add(dayEntered);
            if (dayAttendance != null) attendance = attendance.add(dayAttendance);

            // Only days the employee actually recorded are worth reviewing;
            // an empty non-working day is noise on a 31-row screen.
            if (d.getWorkType() == null && qs.isEmpty()) continue;

            reviewDays.add(new ReviewDay(
                    d.getId(),
                    d.getWorkDate(),
                    d.getWorkDate().getDayOfWeek().name(),
                    d.getWorkType() == null ? null : d.getWorkType().name(),
                    d.getEntrySource() == null ? null : d.getEntrySource().name(),
                    d.getApprovalState().name(),
                    d.getReturnReason(),
                    false,
                    dayEntered,
                    dayAttendance,
                    d.getAttendanceVarianceHours(),
                    d.getVarianceExplanation(),
                    d.getEmployeeNote(),
                    d.getWorkLocation(),
                    d.getProjectId() == null ? null : projectNames.get(d.getProjectId()),
                    qs.stream().map(q -> toQuantityView(q, catalog)).toList(),
                    List.of()));
        }

        Map<String, BigDecimal> totals = totalsOf(ts.getId());
        String blockedReason = actionBlockedReason(ts);

        return new ReviewView(
                ts.getId(),
                ts.getEmployeeId(),
                employee == null ? null : employee.getEmployeeNo(),
                nameOf(employee),
                employee == null ? null : employee.getPositionTitle(),
                ts.getPeriodYear(),
                ts.getPeriodMonth(),
                ts.getStatus().name(),
                blockedReason == null,
                blockedReason,
                ts.getSubmittedAt(),
                ts.getEmployeeComment(),
                entered,
                attendance,
                entered.subtract(attendance),
                totals,
                reviewDays,
                warningsOf(ts),
                corrections.forTimesheet(ts.getId()));
    }

    // ---------- Decisions ----------

    @Transactional
    public ReviewView approve(UUID timesheetId, ApproveRequest req) {
        Timesheet ts = actionable(timesheetId);

        List<TimesheetDay> allDays = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
        long stillReturned = allDays.stream()
                .filter(d -> d.getApprovalState() == DayApprovalState.RETURNED)
                .count();
        if (stillReturned > 0) {
            throw new BadRequestException(stillReturned + " day(s) are still returned for "
                    + "correction. The employee must fix and resubmit them before the month "
                    + "can be approved.");
        }

        String comment = req == null || req.comment() == null ? "" : req.comment();

        // The chain itself is NOT decided here. The TIMESHEET_APPROVAL
        // definition owns it: which steps exist, who approves each one, SLAs,
        // escalation, substitutes. This method records one decision and lets
        // the engine work out whether that advances a step or finishes the
        // month — so adding a step is a configuration change, not a release.
        if (ts.getWorkflowInstanceId() == null) {
            throw new BadRequestException(
                    "This timesheet has no approval workflow attached. It was submitted before "
                            + "workflow routing was enabled — the employee should recall and "
                            + "resubmit it.");
        }
        workflowService.act(ts.getWorkflowInstanceId(),
                new ActionRequest(ActionType.APPROVE, comment, null, null));

        // The engine has advanced (or completed) the instance and, on
        // completion, its event has already driven the month to APPROVED.
        // Anything still running is mid-chain; project that onto the status the
        // employee and payroll read.
        Timesheet after = timesheets.findById(timesheetId).orElse(ts);
        WorkflowInstance instance = workflowService.get(after.getWorkflowInstanceId());
        if (after.getStatus() != TimesheetStatus.APPROVED
                && !instance.getStatus().isTerminal()) {
            after.setStatus(TimesheetStatus.PENDING_HR);
            after.setManagerApprovedAt(OffsetDateTime.now());
            after.setManagerApprovedBy(currentRequest.username());
            timesheets.save(after);
        }

        audit.record(MODULE, ENTITY, ts.getId().toString(), "APPROVAL_DECISION", null,
                Map.of("period", ts.getPeriodYear() + "-" + ts.getPeriodMonth(),
                        "workflowStep", instance.getCurrentStepIndex(),
                        "workflowStatus", instance.getStatus().name(),
                        "comment", comment));
        return review(timesheetId);
    }

    /**
     * Send named days back. Everything not named stays approved, so the
     * employee re-checks two days rather than thirty-one.
     */
    @Transactional
    public ReviewView returnForCorrection(UUID timesheetId, ReturnRequest req) {
        Timesheet ts = actionable(timesheetId);
        if (req == null || req.reason() == null || req.reason().isBlank()) {
            throw new BadRequestException(
                    "A reason is required — the employee needs to know what to fix.");
        }
        if (req.dates() == null || req.dates().isEmpty()) {
            throw new BadRequestException(
                    "Name at least one day to return. To reject the whole submission, use reject.");
        }

        List<TimesheetDay> allDays = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
        Map<LocalDate, TimesheetDay> byDate = allDays.stream()
                .collect(Collectors.toMap(TimesheetDay::getWorkDate, Function.identity(),
                        (a, b) -> a));

        String actor = currentRequest.username();
        OffsetDateTime now = OffsetDateTime.now();
        List<String> returned = new ArrayList<>();

        for (LocalDate date : req.dates()) {
            TimesheetDay day = byDate.get(date);
            if (day == null) {
                throw new BadRequestException("No day " + date + " in this timesheet.");
            }
            day.setApprovalState(DayApprovalState.RETURNED);
            day.setReturnReason(req.reason());
            day.setReturnedBy(actor);
            day.setReturnedAt(now);
            returned.add(date.toString());
        }
        // Days not named are accepted here and now, so a later approve does not
        // silently re-judge them.
        for (TimesheetDay d : allDays) {
            if (d.getApprovalState() == DayApprovalState.RETURNED) continue;
            if (d.getWorkType() == null) continue;
            d.setApprovalState(DayApprovalState.APPROVED);
            d.setApprovedBy(actor);
            d.setApprovedAt(now);
        }
        days.saveAll(allDays);

        ts.setStatus(TimesheetStatus.RETURNED);
        ts.setReturnedAt(now);
        ts.setReturnedBy(actor);
        ts.setReturnReason(req.reason());
        timesheets.save(ts);

        audit.record(MODULE, ENTITY, ts.getId().toString(), "MANAGER_RETURN", null,
                Map.of("dates", String.join(",", returned), "reason", req.reason()));
        return review(timesheetId);
    }

    @Transactional
    public ReviewView reject(UUID timesheetId, RejectRequest req) {
        Timesheet ts = actionable(timesheetId);
        if (req == null || req.reason() == null || req.reason().isBlank()) {
            throw new BadRequestException("A reason is required to reject a timesheet.");
        }

        String actor = currentRequest.username();
        OffsetDateTime now = OffsetDateTime.now();

        // A reject sends the whole month back to the employee as a draft; the
        // per-day verdicts are cleared because none of them stands.
        List<TimesheetDay> allDays = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
        for (TimesheetDay d : allDays) {
            d.setApprovalState(DayApprovalState.PENDING);
            d.setApprovedBy(null);
            d.setApprovedAt(null);
        }
        days.saveAll(allDays);

        ts.setStatus(TimesheetStatus.DRAFT);
        ts.setRejectedAt(now);
        ts.setRejectedBy(actor);
        ts.setRejectionReason(req.reason());
        ts.setSubmittedAt(null);
        ts.setEmployeeConfirmedAt(null);
        timesheets.save(ts);

        audit.record(MODULE, ENTITY, ts.getId().toString(), "MANAGER_REJECT", null,
                Map.of("reason", req.reason()));
        return review(timesheetId);
    }

    /**
     * Approve several clean months at once.
     *
     * <p>Anything not clean is skipped with a stated reason rather than
     * silently dropped — a bulk action that quietly does less than asked is how
     * unapproved months reach a locked period.
     */
    /**
     * Deliberately NOT {@code @Transactional}. Each month is approved in its
     * own transaction — see {@link #approveOneForBulk(UUID, String)}.
     *
     * <p>This method used to be transactional, and the per-item catch below
     * could not do its job because of it: a failure anywhere marks the
     * surrounding transaction rollback-only, and catching the exception does
     * not clear that mark. The loop would carry on, record its skip reasons,
     * and then the commit would fail with UnexpectedRollbackException — a 500
     * in which nothing at all was approved and the manager was told nothing
     * about which month was at fault or why. One bad timesheet silently voided
     * the whole batch.
     */
    public BulkApproveResult bulkApprove(List<UUID> ids, String comment) {
        List<UUID> approved = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();

        for (UUID id : ids == null ? List.<UUID>of() : ids) {
            try {
                String skipReason = inOwnTransaction.execute(
                        status -> approveOneForBulk(id, comment));
                if (skipReason == null) {
                    approved.add(id);
                } else {
                    skipped.put(id.toString(), skipReason);
                }
            } catch (RuntimeException e) {
                // Now genuinely per-item: that month's transaction rolled back
                // on its own and the ones already approved are untouched.
                skipped.put(id.toString(), e.getMessage());
            }
        }
        audit.record(MODULE, ENTITY, "bulk", "MANAGER_BULK_APPROVE", null,
                Map.of("approved", approved.size(), "skipped", skipped.size()));
        return new BulkApproveResult(approved, skipped);
    }

    /**
     * Approves one month inside its own transaction.
     *
     * <p>Its own transaction is the whole point: a month that fails must roll
     * back alone, leaving the months already approved committed and the batch
     * free to continue. The caller wraps this in
     * {@link #inOwnTransaction}.
     *
     * @return null when approved, or the reason it was skipped
     */
    private String approveOneForBulk(UUID id, String comment) {
        Timesheet ts = actionable(id);
        if (blockingCount(ts) > 0) {
            return "Has blocking validation issues — review it individually.";
        }
        if (returnedDayCount(ts) > 0) {
            return "Has days returned for correction.";
        }
        approve(id, new ApproveRequest(comment));
        return null;
    }

    // ---------- Guards ----------

    /** The timesheet, or 404 if it is outside the caller's hierarchy. */
    private Timesheet accessible(UUID id) {
        Timesheet ts = timesheets.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found: " + id));
        // M330 — nomination is access, but only to this employee's TIMESHEETS.
        // It is not a scope widening: nothing else in the system reads it, so a
        // named approver still cannot see the person's salary, documents or
        // record. Without it the approver 404s on the very month the workflow
        // is waiting for them to sign.
        if (!accessScope.isAccessible(ts.getEmployeeId())
                && !nominatedEmployeeIds().contains(ts.getEmployeeId())
                && !reportingLineEmployeeIds().contains(ts.getEmployeeId())) {
            // 404 not 403: the queue must not confirm that a timesheet exists
            // for someone the caller may not see.
            throw new ResourceNotFoundException("Timesheet not found: " + id);
        }
        return ts;
    }

    /** Accessible, in an actionable state, in an open period, and not the caller's own. */
    private Timesheet actionable(UUID id) {
        Timesheet ts = accessible(id);
        String blocked = actionBlockedReason(ts);
        if (blocked != null) throw new BadRequestException(blocked);
        return ts;
    }

    /** Why this timesheet cannot be acted on right now, or null when it can. */
    private String actionBlockedReason(Timesheet ts) {
        UUID self = currentEmployeeIdOrNull();
        if (self != null && ts.getEmployeeId().equals(self)) {
            return "You cannot approve your own timesheet.";
        }
        if (periods.isLocked(ts.getPeriodYear(), ts.getPeriodMonth())) {
            return "Period " + ts.getPeriodYear() + "-"
                    + String.format("%02d", ts.getPeriodMonth())
                    + " is locked. Unlock it before making approval decisions.";
        }
        if (!ACTIONABLE.contains(ts.getStatus())) {
            return "This timesheet is " + ts.getStatus() + " — there is nothing to decide.";
        }
        return null;
    }

    /**
     * M330 — the employees who name the caller as their timesheet approver.
     * Empty for almost everyone, so the query is cheap and short-circuits.
     */
    /**
     * Employees whose month this caller approves by reporting line: their
     * direct reports, plus anyone whose manager has delegated to them right
     * now.
     *
     * <p>Separate from the access scope on purpose. Scope is granted by role,
     * and a line manager frequently holds only the EMPLOYEE role — the
     * workflow routed them the month and the queue then hid it, so it sat
     * there with nobody able to see whose move it was. Being the manager of
     * record is the authority; the role is a different question.
     */
    private Set<UUID> reportingLineEmployeeIds() {
        UUID me = currentEmployeeIdOrNull();
        if (me == null) return Set.of();
        Set<UUID> out = new java.util.HashSet<>(employees.findIdsByManagerId(me));
        out.addAll(employees.findIdsByManagerDelegatedTo(me, java.time.LocalDate.now()));
        return out;
    }

    private Set<UUID> nominatedEmployeeIds() {
        UUID me = currentEmployeeIdOrNull();
        if (me == null) return Set.of();
        return Set.copyOf(employees.findIdsByTimesheetApproverId(me));
    }

    private UUID currentEmployeeIdOrNull() {
        String username = currentRequest.username();
        if (username == null || username.isBlank()) return null;
        return employees.findByUsername(username).map(Employee::getId).orElse(null);
    }

    // ---------- Helpers ----------

    private QueueRow toQueueRow(Timesheet ts, Employee employee) {
        List<TimesheetDay> allDays = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
        int entered = (int) allDays.stream().filter(d -> d.getWorkType() != null).count();
        int returnedDays = (int) allDays.stream()
                .filter(d -> d.getApprovalState() == DayApprovalState.RETURNED).count();
        int warnings = warningsOf(ts).size();
        int blocking = blockingCount(ts);

        return new QueueRow(
                ts.getId(),
                ts.getEmployeeId(),
                employee == null ? null : employee.getEmployeeNo(),
                nameOf(employee),
                employee == null ? null : employee.getPositionTitle(),
                ts.getPeriodYear(),
                ts.getPeriodMonth(),
                ts.getStatus().name(),
                ts.getTotalWorkedHours(),
                ts.getTotalOvertimeHours(),
                entered,
                returnedDays,
                warnings,
                blocking,
                blocking == 0 && returnedDays == 0 && ts.getStatus() == TimesheetStatus.SUBMITTED,
                ts.getSubmittedAt());
    }

    /**
     * Warnings the employee's submission carried, replayed from the stored
     * text. Kept as the submission-time snapshot deliberately: the manager
     * judges what was submitted, not what a re-run of validation says today.
     */
    private List<FindingView> warningsOf(Timesheet ts) {
        String raw = ts.getValidationWarnings();
        if (raw == null || raw.isBlank()) return List.of();
        return raw.lines()
                .filter(l -> !l.isBlank())
                .map(l -> new FindingView("SUBMITTED_WARNING", "WARNING", null, l))
                .toList();
    }

    private int blockingCount(Timesheet ts) {
        // A submitted month passed blocking validation by construction; anything
        // blocking here would have to have appeared afterwards.
        return 0;
    }

    private int returnedDayCount(Timesheet ts) {
        return (int) days.findByTimesheetIdOrderByWorkDateAsc(ts.getId()).stream()
                .filter(d -> d.getApprovalState() == DayApprovalState.RETURNED)
                .count();
    }

    private Map<UUID, Employee> employeesOf(List<Timesheet> list) {
        Set<UUID> ids = list.stream().map(Timesheet::getEmployeeId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return employees.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));
    }

    private String nameOf(Employee e) {
        if (e == null) return null;
        return e.getLastName() + ", " + e.getFirstName();
    }

    private Map<String, TimeCategory> catalogByCode() {
        return categories.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .collect(Collectors.toMap(TimeCategory::getCode, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<UUID, List<DayQuantity>> quantitiesByDay(List<TimesheetDay> allDays) {
        List<UUID> ids = allDays.stream().map(TimesheetDay::getId).toList();
        if (ids.isEmpty()) return Map.of();
        Map<UUID, List<DayQuantity>> byDay = new HashMap<>();
        for (DayQuantity q : quantities.findByTimesheetDayIdIn(ids)) {
            byDay.computeIfAbsent(q.getTimesheetDayId(), k -> new ArrayList<>()).add(q);
        }
        return byDay;
    }

    private Map<String, BigDecimal> totalsOf(UUID timesheetId) {
        return monthTotals.findByTimesheetIdOrderByCategoryCodeAsc(timesheetId).stream()
                .collect(Collectors.toMap(TimesheetMonthTotal::getCategoryCode,
                        TimesheetMonthTotal::getQuantity, BigDecimal::add, LinkedHashMap::new));
    }

    /**
     * Hours the day contains. Derived quantities re-classify hours already
     * counted, so including them would show the manager a doubled day.
     */
    private BigDecimal baseHours(List<DayQuantity> qs, Map<String, TimeCategory> catalog) {
        BigDecimal sum = BigDecimal.ZERO;
        for (DayQuantity q : qs) {
            if (q.isDerived()) continue;
            TimeCategory cat = catalog.get(q.getCategoryCode());
            if (cat == null || cat.isDays()) continue;
            sum = sum.add(q.getQuantity());
        }
        return sum;
    }

    private QuantityView toQuantityView(DayQuantity q, Map<String, TimeCategory> catalog) {
        TimeCategory cat = catalog.get(q.getCategoryCode());
        return new QuantityView(q.getCategoryCode(),
                cat == null ? q.getCategoryCode() : cat.getName(),
                cat == null ? "HOURS" : cat.getUnit(),
                q.getQuantity(), q.isDerived(), q.getDerivedFrom());
    }

    private BigDecimal attendanceHours(UUID employeeId, LocalDate date) {
        return summaries.findByEmployeeIdAndWorkDate(employeeId, date)
                .map(DailySummary::getWorkedMinutes)
                .map(m -> BigDecimal.valueOf(m).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP))
                .orElse(null);
    }
}
