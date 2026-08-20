package az.millers.hcm.timesheet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.ScheduleAssignment;
import az.millers.hcm.attendance.domain.WorkSchedule;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.attendance.repo.ScheduleAssignmentRepository;
import az.millers.hcm.attendance.repo.WorkScheduleRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.tenant.TenantContext;
import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.service.HolidayService;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.BulkDayEntry;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.BulkDayEntryRequest;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.CategoryOption;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.DayEntryRequest;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.DayView;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.FindingView;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.MonthView;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.ProjectOption;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.QuantityInput;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.QuantityView;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.SubmitRequest;
import az.millers.hcm.timesheet.domain.DayApprovalState;
import az.millers.hcm.timesheet.domain.DayQuantity;
import az.millers.hcm.timesheet.domain.EntrySource;
import az.millers.hcm.timesheet.domain.TimeCategory;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetDay;
import az.millers.hcm.timesheet.domain.TimesheetMonthTotal;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.domain.WorkType;
import az.millers.hcm.timesheet.repo.DayQuantityRepository;
import az.millers.hcm.timesheet.repo.TimeCategoryRepository;
import az.millers.hcm.timesheet.repo.TimesheetDayRepository;
import az.millers.hcm.timesheet.repo.TimesheetMonthTotalRepository;
import az.millers.hcm.timesheet.repo.TimesheetProjectRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * Employee-facing daily timesheet capture.
 *
 * <p>Inverts the original model: the employee declares what they did and
 * attendance becomes corroborating evidence rather than the source of truth.
 * That is a requirement, not a preference — offshore crews work at facilities
 * with no biometric device, so an attendance-generated timesheet is empty for
 * exactly the people whose pay is most complex.
 *
 * <p>Every method takes the employee id from the caller's own context. Nothing
 * here accepts an arbitrary employee id, so there is no path by which one
 * employee reaches another's timesheet.
 *
 * <p>Contains no monetary logic whatsoever.
 */
@Service
public class TimesheetEntryService {

    private static final String MODULE = "TIMESHEET";
    private static final String ENTITY = "TimesheetDay";
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    /** V322 — overtime is typed in minutes and paid in rounded hours. */
    private static final String OVERTIME_MINUTES = "ONSHORE_OVERTIME_MINUTES";
    private static final String OVERTIME_HOURS = "ONSHORE_OVERTIME_HOURS";
    private static final String OVERTIME_ROUNDING_KEY = "timesheet.overtime.rounding-minutes";
    private static final String WORK_LOCATIONS_KEY = "timesheet.work-locations";

    private static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private final TimesheetRepository timesheets;
    private final TimesheetDayRepository days;
    private final DayQuantityRepository quantities;
    private final TimesheetMonthTotalRepository monthTotals;
    private final TimeCategoryRepository categories;
    private final DayQuantityDeriver deriver;
    private final TimesheetValidator validator;
    private final LeaveRequestRepository leaves;
    private final LeaveTypeRepository leaveTypes;
    private final DailySummaryRepository summaries;
    private final ScheduleAssignmentRepository assignments;
    private final WorkScheduleRepository schedules;
    private final HolidayService holidays;
    private final TimesheetProjectRepository projects;
    /** V322: overtime rounding step and the permitted work-location list. */
    private final SettingService settings;
    /** The approval route lives in the engine, not in this class. */
    private final WorkflowService workflowService;
    private final TimesheetPeriodService periods;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public TimesheetEntryService(TimesheetRepository timesheets,
                                 TimesheetDayRepository days,
                                 DayQuantityRepository quantities,
                                 TimesheetMonthTotalRepository monthTotals,
                                 TimeCategoryRepository categories,
                                 DayQuantityDeriver deriver,
                                 TimesheetValidator validator,
                                 LeaveRequestRepository leaves,
                                 LeaveTypeRepository leaveTypes,
                                 DailySummaryRepository summaries,
                                 ScheduleAssignmentRepository assignments,
                                 WorkScheduleRepository schedules,
                                 HolidayService holidays,
                                 TimesheetProjectRepository projects,
                                 SettingService settings,
                                 WorkflowService workflowService,
                                 TimesheetPeriodService periods,
                                 AuditService audit,
                                 CurrentRequest currentRequest) {
        this.timesheets = timesheets;
        this.days = days;
        this.quantities = quantities;
        this.monthTotals = monthTotals;
        this.categories = categories;
        this.deriver = deriver;
        this.validator = validator;
        this.leaves = leaves;
        this.leaveTypes = leaveTypes;
        this.summaries = summaries;
        this.assignments = assignments;
        this.schedules = schedules;
        this.holidays = holidays;
        this.projects = projects;
        this.settings = settings;
        this.workflowService = workflowService;
        this.periods = periods;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ---------- Read ----------

    /**
     * The employee's month, creating it in DRAFT on first open.
     *
     * <p>Auto-creation is deliberate: an employee should never have to ask HR to
     * "generate" the month before they can record the day they just worked.
     */
    @Transactional
    public MonthView openMonth(UUID employeeId, int year, int month) {
        Timesheet ts = timesheets.findByEmployeeIdAndPeriodYearAndPeriodMonth(employeeId, year, month)
                .orElseGet(() -> createDraft(employeeId, year, month));
        return view(ts, employeeId);
    }

    // ---------- Write ----------

    /** Upsert one day: store what the employee said, then derive what follows. */
    @Transactional
    public MonthView saveDay(UUID employeeId, int year, int month, LocalDate date,
                             DayEntryRequest req) {
        Timesheet ts = requireEditable(employeeId, year, month);
        YearMonth ym = YearMonth.of(year, month);
        if (date.isBefore(ym.atDay(1)) || date.isAfter(ym.atEndOfMonth())) {
            throw new BadRequestException("Date " + date + " is outside " + ym + ".");
        }

        Map<String, TimeCategory> catalog = catalogByCode();
        LeaveRequest leave = leaveCovering(employeeId, date);
        if (leave != null) {
            throw new BadRequestException(
                    "This day is covered by approved leave request " + leave.getRequestNo()
                            + " and cannot be edited here. Cancel the leave request first.");
        }

        WorkType workType = WorkType.parse(req.workType());
        if (workType == null) {
            throw new BadRequestException("A work type is required.");
        }

        TimesheetDay day = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId()).stream()
                .filter(d -> d.getWorkDate().equals(date))
                .findFirst()
                .orElseGet(() -> {
                    TimesheetDay d = new TimesheetDay();
                    d.setTimesheetId(ts.getId());
                    d.setWorkDate(date);
                    d.setPrimaryCode("W");
                    return d;
                });

        assertDayOpen(ts, day);

        day.setWorkType(workType);
        day.setEntrySource(EntrySource.EMPLOYEE);
        // A day that changes is a day that needs judging again.
        day.setApprovalState(DayApprovalState.PENDING);
        day.setReturnReason(null);
        day.setPrimaryCode(primaryCodeFor(workType));
        day.setEmployeeNote(req.note());
        day.setVarianceExplanation(req.varianceExplanation());
        // V322 — location and cost attribution travel with the day.
        day.setWorkLocation(trimToNull(req.workLocation()));
        day.setProjectId(req.projectId());
        day.setTaskCode(trimToNull(req.taskCode()));

        // Employee-declared quantities, minus anything the system owns.
        List<DayQuantity> incoming = new ArrayList<>();
        BigDecimal declaredHours = BigDecimal.ZERO;
        for (QuantityInput in : Optional.ofNullable(req.quantities()).orElse(List.of())) {
            TimeCategory cat = catalog.get(in.categoryCode());
            if (cat == null) {
                throw new BadRequestException("Unknown time category: " + in.categoryCode());
            }
            if (DayQuantityDeriver.isDerived(cat)) {
                // Silently ignoring would hide a real client bug; refusing tells
                // the caller the system owns this number. Note V321: nightshift,
                // public-holiday and leave hours are no longer owned — the crew
                // types them and the deriver cross-checks below.
                throw new BadRequestException(
                        cat.getName() + " is calculated by the system and cannot be entered.");
            }
            BigDecimal value = in.quantity() == null ? BigDecimal.ZERO : in.quantity();
            if (value.signum() == 0) continue;
            DayQuantity q = new DayQuantity(day.getId(), cat.getCode(), value);
            q.setOverrideReason(in.overrideReason());
            incoming.add(q);
            // Only hour-denominated categories sum to the day's hours: DAYS are
            // allowance flags and MINUTES is raw overtime awaiting rounding.
            if (cat.isHours()) declaredHours = declaredHours.add(value);
        }

        // V322 — payable overtime is DERIVED from the actual minutes the
        // employee typed, rounded by the company rule. Never taken from the
        // client: rounding overtime changes pay, so the server owns it.
        BigDecimal otMinutes = incoming.stream()
                .filter(q -> OVERTIME_MINUTES.equals(q.getCategoryCode()))
                .map(DayQuantity::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otHours = roundedOvertimeHours(otMinutes);
        incoming.removeIf(q -> OVERTIME_HOURS.equals(q.getCategoryCode()));
        if (otHours.signum() > 0) {
            DayQuantity rounded = new DayQuantity(day.getId(), OVERTIME_HOURS, otHours);
            rounded.setDerivedFrom("OVERTIME_ROUNDING");
            incoming.add(rounded);
        }

        day.setWorkedHours(declaredHours);
        day.setOvertimeHours(otHours);

        // Attendance is evidence, not truth: record the gap, never overwrite.
        BigDecimal attendanceHours = attendanceHours(employeeId, date);
        day.setAttendanceVarianceHours(
                attendanceHours == null ? null : declaredHours.subtract(attendanceHours));

        TimesheetDay savedDay = days.save(day);

        // Replace this day's quantities wholesale — simpler and safer than
        // diffing, and the audit record carries the before/after anyway.
        List<DayQuantity> before = quantities.findByTimesheetDayId(savedDay.getId());
        quantities.deleteByTimesheetDayId(savedDay.getId());
        quantities.flush();

        boolean isHoliday = holidays.holidayDatesIn(ym).contains(date);
        for (DayQuantity q : incoming) {
            q.setTimesheetDayId(savedDay.getId());
        }
        for (DayQuantity derived : deriver.derive(employeeId, date, workType,
                declaredHours, isHoliday, null)) {
            derived.setTimesheetDayId(savedDay.getId());
            incoming.add(derived);
        }
        quantities.saveAll(incoming);

        recomputeTotals(ts);

        audit.record(MODULE, ENTITY, savedDay.getId().toString(), "DAY_ENTRY",
                Map.of("quantities", before.stream()
                        .collect(Collectors.toMap(DayQuantity::getCategoryCode,
                                q -> q.getQuantity().toPlainString(), (a, b) -> a))),
                Map.of("date", date.toString(),
                        "workType", workType.name(),
                        "quantities", incoming.stream()
                                .collect(Collectors.toMap(DayQuantity::getCategoryCode,
                                        q -> q.getQuantity().toPlainString(), (a, b) -> a))));

        return view(ts, employeeId);
    }

    /**
     * Apply a grid's worth of edits in one transaction.
     *
     * <p>A rotation month is ~20 edited days. Saving them one request at a time
     * is slow, and worse, non-atomic: a failure on day 14 leaves a half-entered
     * month that the employee has to reconcile by eye. Here the whole grid lands
     * or none of it does, and the month totals are recomputed once at the end
     * rather than 20 times.
     *
     * <p>Per-day validation is unchanged — each day still goes through
     * {@link #saveDay}, so a blocking finding on any day rolls the batch back
     * with the message naming the date.
     */
    @Transactional
    public MonthView saveDays(UUID employeeId, int year, int month, BulkDayEntryRequest req) {
        List<BulkDayEntry> entries = Optional.ofNullable(req)
                .map(BulkDayEntryRequest::days)
                .orElse(List.of());
        if (entries.isEmpty()) {
            throw new BadRequestException("No days to save.");
        }

        // Deterministic order: the two-consecutive-day overtime rule and the
        // "copy previous" semantics both read earlier days, so apply ascending.
        List<BulkDayEntry> ordered = entries.stream()
                .filter(e -> e != null && e.date() != null)
                .sorted(Comparator.comparing(BulkDayEntry::date))
                .toList();

        Timesheet ts = requireEditable(employeeId, year, month);
        for (BulkDayEntry entry : ordered) {
            try {
                if (entry.entry() == null) {
                    clearDay(employeeId, year, month, entry.date());
                } else {
                    saveDay(employeeId, year, month, entry.date(), entry.entry());
                }
            } catch (BadRequestException ex) {
                // Without the date the employee cannot tell which of 20 rows is
                // at fault, and the whole batch has just rolled back.
                throw new BadRequestException(entry.date() + ": " + ex.getMessage());
            }
        }
        return view(ts, employeeId);
    }

    /** Copy the nearest earlier day that has an entry — the common rota case. */
    @Transactional
    public MonthView copyPreviousDay(UUID employeeId, int year, int month, LocalDate date) {
        Timesheet ts = requireEditable(employeeId, year, month);
        TimesheetDay source = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId()).stream()
                .filter(d -> d.getWorkDate().isBefore(date) && d.getWorkType() != null)
                .max(Comparator.comparing(TimesheetDay::getWorkDate))
                .orElseThrow(() -> new BadRequestException(
                        "There is no earlier completed day in this month to copy."));

        List<QuantityInput> inputs = quantities.findByTimesheetDayId(source.getId()).stream()
                .filter(q -> !q.isDerived())
                .map(q -> new QuantityInput(q.getCategoryCode(), q.getQuantity(), null))
                .toList();

        return saveDay(employeeId, year, month, date, new DayEntryRequest(
                source.getWorkType().name(), inputs, source.getEmployeeNote(), null));
    }

    /** Clear a day back to "not entered". */
    @Transactional
    public MonthView clearDay(UUID employeeId, int year, int month, LocalDate date) {
        Timesheet ts = requireEditable(employeeId, year, month);
        days.findByTimesheetIdOrderByWorkDateAsc(ts.getId()).stream()
                .filter(d -> d.getWorkDate().equals(date))
                .findFirst()
                .ifPresent(d -> {
                    quantities.deleteByTimesheetDayId(d.getId());
                    d.setWorkType(null);
                    d.setWorkedHours(BigDecimal.ZERO);
                    d.setOvertimeHours(BigDecimal.ZERO);
                    d.setAttendanceVarianceHours(null);
                    // V322 fields belong to the entry, not to the empty row. Left
                    // behind, a cleared day still shows a work location in the
                    // grid — an entry that reads as real but has no hours.
                    d.setWorkLocation(null);
                    d.setProjectId(null);
                    d.setTaskCode(null);
                    d.setEmployeeNote(null);
                    days.save(d);
                    audit.record(MODULE, ENTITY, d.getId().toString(), "DAY_CLEARED",
                            null, Map.of("date", date.toString()));
                });
        recomputeTotals(ts);
        return view(ts, employeeId);
    }

    /** Validate the month and hand it to the approval workflow. */
    @Transactional
    public MonthView submit(UUID employeeId, int year, int month, SubmitRequest req) {
        Timesheet ts = requireEditable(employeeId, year, month);
        if (req == null || !req.confirmed()) {
            throw new BadRequestException(
                    "Confirm that the submitted working time is accurate before submitting.");
        }

        List<TimesheetValidator.Finding> findings = validateAll(ts, employeeId);
        List<TimesheetValidator.Finding> blocking = findings.stream()
                .filter(TimesheetValidator.Finding::isBlocking)
                .toList();
        if (!blocking.isEmpty()) {
            throw new BadRequestException("Timesheet cannot be submitted: "
                    + blocking.stream().map(TimesheetValidator.Finding::message)
                            .collect(Collectors.joining(" ")));
        }

        String warnings = findings.stream()
                .map(f -> f.code() + (f.date() == null ? "" : " " + f.date()) + ": " + f.message())
                .collect(Collectors.joining("\n"));

        ts.setStatus(TimesheetStatus.SUBMITTED);
        ts.setSubmittedAt(OffsetDateTime.now());
        ts.setSubmittedBy(currentRequest.username());
        ts.setEmployeeConfirmedAt(OffsetDateTime.now());
        ts.setEmployeeComment(req.comment());
        ts.setValidationWarnings(warnings.isBlank() ? null : warnings);

        // The approval route is DATA, not code: the TIMESHEET_APPROVAL
        // definition (Workflow & Approvals → Workflow Definitions) declares the
        // steps — today "Manager review", resolved to the employee's own
        // manager, then HR sign-off. Adding a step or changing an approver is a
        // configuration change, not a deploy.
        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                TimesheetService.WORKFLOW_DEFINITION,
                MODULE,
                "Timesheet",
                ts.getId().toString(),
                "Timesheet — " + year + "/" + String.format("%02d", month)
                        + " — " + ts.getTotalWorkedHours() + "h",
                Map.of("employeeId", employeeId.toString(),
                       "period", year + "-" + month)));
        ts.setWorkflowInstanceId(instance.getId());
        timesheets.save(ts);

        audit.record(MODULE, "Timesheet", ts.getId().toString(), "EMPLOYEE_SUBMIT", null,
                Map.of("period", year + "-" + month, "warnings", findings.size(),
                        "workflowInstanceId", instance.getId().toString()));
        return view(ts, employeeId);
    }

    /** Pull back a submission that has not been approved yet. */
    @Transactional
    public MonthView recall(UUID employeeId, int year, int month) {
        Timesheet ts = own(employeeId, year, month);
        if (ts.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new BadRequestException(
                    "Only a SUBMITTED timesheet can be recalled (current: " + ts.getStatus() + ").");
        }
        ts.setStatus(TimesheetStatus.DRAFT);
        ts.setSubmittedAt(null);
        ts.setEmployeeConfirmedAt(null);
        timesheets.save(ts);
        audit.record(MODULE, "Timesheet", ts.getId().toString(), "EMPLOYEE_RECALL", null,
                Map.of("period", year + "-" + month));
        return view(ts, employeeId);
    }

    // ---------- Internals ----------

    private Timesheet createDraft(UUID employeeId, int year, int month) {
        Timesheet ts = new Timesheet();
        ts.setEmployeeId(employeeId);
        ts.setPeriodYear(year);
        ts.setPeriodMonth(month);
        ts.setStatus(TimesheetStatus.DRAFT);
        ts.setGeneratedBy(currentRequest.username());
        return timesheets.save(ts);
    }

    /** The employee's own month, or 404 — never another employee's. */
    private Timesheet own(UUID employeeId, int year, int month) {
        Timesheet ts = timesheets.findByEmployeeIdAndPeriodYearAndPeriodMonth(employeeId, year, month)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No timesheet for " + year + "-" + month + "."));
        if (!ts.getEmployeeId().equals(employeeId)) {
            throw new ResourceNotFoundException("No timesheet for " + year + "-" + month + ".");
        }
        return ts;
    }

    private Timesheet requireEditable(UUID employeeId, int year, int month) {
        // A locked period refuses edits before anything else is considered —
        // it is the gate payroll relies on.
        periods.assertOpen(year, month);
        Timesheet ts = timesheets.findByEmployeeIdAndPeriodYearAndPeriodMonth(employeeId, year, month)
                .orElseGet(() -> createDraft(employeeId, year, month));
        if (!ts.getStatus().isEditableByEmployee()) {
            throw new BadRequestException(
                    "This timesheet is " + ts.getStatus() + " and can no longer be edited."
                            + (ts.getStatus() == TimesheetStatus.SUBMITTED
                                    ? " Recall it first."
                                    : " Raise a correction request to change it."));
        }
        return ts;
    }

    /**
     * After a partial return, only the days the approver named are open.
     *
     * <p>Leaving the whole month editable would let an employee quietly change
     * days a manager already accepted, which would make the per-day approval
     * meaningless.
     */
    private void assertDayOpen(Timesheet ts, TimesheetDay day) {
        if (ts.getStatus() != TimesheetStatus.RETURNED
                && ts.getStatus() != TimesheetStatus.REOPENED) {
            return;
        }
        if (day.getId() == null) return;   // a day that did not exist cannot have been approved
        if (day.getApprovalState() == DayApprovalState.APPROVED) {
            throw new BadRequestException(
                    "Your manager already approved " + day.getWorkDate()
                            + ". Only the days sent back for correction can be changed.");
        }
    }

    private Map<String, TimeCategory> catalogByCode() {
        return categories.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .collect(Collectors.toMap(TimeCategory::getCode, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String primaryCodeFor(WorkType type) {
        return switch (type) {
            case LEAVE -> "L";
            case SICK -> "S";
            case BUSINESS_TRIP -> "BT";
            case NON_WORKING -> "H";
            default -> "W";
        };
    }

    private LeaveRequest leaveCovering(UUID employeeId, LocalDate date) {
        return leaves.findApprovedOverlapping(employeeId, date, date).stream()
                .filter(r -> !date.isBefore(r.getStartDate()) && !date.isAfter(r.getEndDate()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal attendanceHours(UUID employeeId, LocalDate date) {
        return summaries.findByEmployeeIdAndWorkDate(employeeId, date)
                .map(DailySummary::getWorkedMinutes)
                .map(m -> BigDecimal.valueOf(m).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP))
                .orElse(null);
    }

    /** Rebuild the month's per-category totals — the payroll input contract. */
    private void recomputeTotals(Timesheet ts) {
        List<TimesheetDay> allDays = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
        List<UUID> dayIds = allDays.stream().map(TimesheetDay::getId).toList();

        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        if (!dayIds.isEmpty()) {
            for (DayQuantity q : quantities.findByTimesheetDayIdIn(dayIds)) {
                sums.merge(q.getCategoryCode(), q.getQuantity(), BigDecimal::add);
            }
        }

        monthTotals.deleteByTimesheetId(ts.getId());
        monthTotals.flush();
        List<TimesheetMonthTotal> rows = sums.entrySet().stream()
                .map(e -> new TimesheetMonthTotal(ts.getId(), e.getKey(), e.getValue()))
                .toList();
        monthTotals.saveAll(rows);

        // Keep the legacy header totals in step so PayrollEngine and the
        // existing HR screens keep reading correct numbers.
        BigDecimal worked = allDays.stream().map(TimesheetDay::getWorkedHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overtime = allDays.stream().map(TimesheetDay::getOvertimeHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ts.setTotalWorkedHours(worked);
        ts.setTotalOvertimeHours(overtime);
        timesheets.save(ts);
    }

    private List<TimesheetValidator.Finding> validateAll(Timesheet ts, UUID employeeId) {
        YearMonth ym = YearMonth.of(ts.getPeriodYear(), ts.getPeriodMonth());
        Map<String, TimeCategory> catalog = catalogByCode();
        Set<LocalDate> holidayDates = holidays.holidayDatesIn(ym);
        List<TimesheetDay> allDays = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
        Map<UUID, List<DayQuantity>> byDay = quantitiesByDay(allDays);

        List<TimesheetValidator.Finding> out = new ArrayList<>();
        for (TimesheetDay d : allDays) {
            List<DayQuantity> dayQuantities = byDay.getOrDefault(d.getId(), List.of());
            out.addAll(validator.validateDay(d, catalog, dayQuantities,
                    holidayDates.contains(d.getWorkDate()),
                    isScheduledWorkingDay(employeeId, d.getWorkDate())));
            out.addAll(crossCheck(employeeId, d, dayQuantities,
                    holidayDates.contains(d.getWorkDate())));
        }

        Map<String, BigDecimal> totals = monthTotals.findByTimesheetIdOrderByCategoryCodeAsc(ts.getId())
                .stream()
                .collect(Collectors.toMap(TimesheetMonthTotal::getCategoryCode,
                        TimesheetMonthTotal::getQuantity, BigDecimal::add, LinkedHashMap::new));

        out.addAll(validator.validateMonth(allDays, totals,
                requiredWorkingDays(employeeId, ym), holidayDates));
        return out;
    }

    /**
     * V321 cross-check: the crew types nightshift / public-holiday / leave
     * hours, and the system still works out its own figure from the roster,
     * the holiday calendar and approved leave. Neither number silently wins —
     * where they disagree the day carries a warning to the approver.
     *
     * <p>Only compares where the system has a BASIS to compare: if no roster is
     * assigned the deriver produces nothing, and we stay quiet rather than
     * flagging every offshore day of a tenant that does not keep rosters in the
     * system. A wrong roster is a warning; a missing roster is not news.
     */
    private List<TimesheetValidator.Finding> crossCheck(UUID employeeId, TimesheetDay day,
                                                        List<DayQuantity> stored, boolean isHoliday) {
        if (day.getWorkType() == null) {
            return List.of();
        }
        Map<String, BigDecimal> typed = stored.stream()
                .filter(q -> !q.isDerived())
                .filter(q -> DayQuantityDeriver.isCrossCheckable(q.getCategoryCode()))
                .collect(Collectors.toMap(DayQuantity::getCategoryCode, DayQuantity::getQuantity,
                        BigDecimal::add));
        if (typed.isEmpty()) {
            return List.of();
        }

        List<TimesheetValidator.Finding> out = new ArrayList<>();
        for (DayQuantity system : deriver.derive(employeeId, day.getWorkDate(), day.getWorkType(),
                day.getWorkedHours(), isHoliday, null)) {
            BigDecimal entered = typed.get(system.getCategoryCode());
            if (entered == null || entered.compareTo(system.getQuantity()) == 0) {
                continue;
            }
            out.add(TimesheetValidator.Finding.warning("SYSTEM_VALUE_MISMATCH", day.getWorkDate(),
                    system.getCategoryCode() + ": you entered " + entered.stripTrailingZeros().toPlainString()
                            + " h, the roster/calendar shows "
                            + system.getQuantity().stripTrailingZeros().toPlainString() + " h."));
        }
        return out;
    }

    /**
     * Actual overtime minutes → payable hours, rounded by the company rule.
     *
     * <p>{@code timesheet.overtime.rounding-minutes} (default 30) is a SETTING
     * rather than a constant because it moves money: 29 minutes rounds to zero
     * and 31 to half an hour, and an auditor must be able to see which rule was
     * in force. 0 disables rounding and pays the exact minutes.
     */
    private BigDecimal roundedOvertimeHours(BigDecimal minutes) {
        if (minutes == null || minutes.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int step = intSetting(OVERTIME_ROUNDING_KEY, 30);
        BigDecimal payableMinutes = step <= 0
                ? minutes
                : minutes.divide(BigDecimal.valueOf(step), 0, RoundingMode.HALF_UP)
                         .multiply(BigDecimal.valueOf(step));
        return payableMinutes.divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
    }

    private int intSetting(String key, int fallback) {
        try {
            return Integer.parseInt(settings.get(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Active projects the employee may charge a day to. */
    private List<ProjectOption> projectOptions() {
        return projects.findByTenantIdAndActiveOrderByName(TenantContext.current(), true).stream()
                .map(p -> new ProjectOption(p.getId(), p.getCode(), p.getName()))
                .toList();
    }

    /** Work locations HR permits; empty means the field is free text. */
    private List<String> workLocations() {
        String raw = settings.get(WORK_LOCATIONS_KEY, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
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

    private boolean isScheduledWorkingDay(UUID employeeId, LocalDate date) {
        return assignments.findActiveOn(employeeId, date)
                .map(ScheduleAssignment::getScheduleId)
                .flatMap(schedules::findById)
                .map(s -> s.isWorkingDay(date.getDayOfWeek()))
                .orElse(false);
    }

    /**
     * Days the schedule expects work on. An employee with no schedule has no
     * required days — a rota worker must not be blocked from submitting because
     * the system does not know their pattern.
     */
    private Set<LocalDate> requiredWorkingDays(UUID employeeId, YearMonth ym) {
        Optional<WorkSchedule> schedule = assignments.findActiveOn(employeeId, ym.atDay(1))
                .map(ScheduleAssignment::getScheduleId)
                .flatMap(schedules::findById);
        if (schedule.isEmpty()) return Set.of();

        Set<LocalDate> out = new HashSet<>();
        for (LocalDate d = ym.atDay(1); !d.isAfter(ym.atEndOfMonth()); d = d.plusDays(1)) {
            if (schedule.get().isWorkingDay(d.getDayOfWeek())) out.add(d);
        }
        return out;
    }

    // ---------- View assembly ----------

    private MonthView view(Timesheet ts, UUID employeeId) {
        YearMonth ym = YearMonth.of(ts.getPeriodYear(), ts.getPeriodMonth());
        Map<String, TimeCategory> catalog = catalogByCode();
        Set<LocalDate> holidayDates = holidays.holidayDatesIn(ym);
        List<TimesheetDay> allDays = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId());
        Map<UUID, List<DayQuantity>> byDay = quantitiesByDay(allDays);
        Map<LocalDate, LeaveRequest> leaveByDate = leaveByDate(employeeId, ym);

        List<TimesheetValidator.Finding> findings = validateAll(ts, employeeId);
        Map<LocalDate, List<FindingView>> findingsByDate = new HashMap<>();
        List<FindingView> monthFindings = new ArrayList<>();
        for (TimesheetValidator.Finding f : findings) {
            FindingView v = new FindingView(f.code(), f.severity(), f.date(), f.message());
            // Day findings are attached to their day AND listed at month level,
            // so the summary panel is a complete picture without walking days.
            if (f.date() != null) {
                findingsByDate.computeIfAbsent(f.date(), k -> new ArrayList<>()).add(v);
            }
            monthFindings.add(v);
        }

        boolean editable = ts.getStatus().isEditableByEmployee()
                && !periods.isLocked(ts.getPeriodYear(), ts.getPeriodMonth());

        List<DayView> dayViews = new ArrayList<>();
        Map<LocalDate, TimesheetDay> dayByDate = allDays.stream()
                .collect(Collectors.toMap(TimesheetDay::getWorkDate, Function.identity(),
                        (a, b) -> a));

        for (LocalDate d = ym.atDay(1); !d.isAfter(ym.atEndOfMonth()); d = d.plusDays(1)) {
            TimesheetDay day = dayByDate.get(d);
            LeaveRequest leave = leaveByDate.get(d);
            boolean holiday = holidayDates.contains(d);

            List<QuantityView> qs = day == null ? List.of()
                    : byDay.getOrDefault(day.getId(), List.of()).stream()
                            .map(q -> {
                                TimeCategory cat = catalog.get(q.getCategoryCode());
                                return new QuantityView(q.getCategoryCode(),
                                        cat == null ? q.getCategoryCode() : cat.getName(),
                                        cat == null ? "HOURS" : cat.getUnit(),
                                        q.getQuantity(), q.isDerived(), q.getDerivedFrom());
                            })
                            .toList();

            dayViews.add(new DayView(
                    day == null ? null : day.getId(),
                    d,
                    d.getDayOfWeek().name(),
                    day == null || day.getWorkType() == null ? null : day.getWorkType().name(),
                    day == null ? null : day.getEntrySource().name(),
                    holiday,
                    isScheduledWorkingDay(employeeId, d),
                    !editable || leave != null,
                    leave != null ? "Covered by approved leave " + leave.getRequestNo() : null,
                    leave == null ? null : leave.getId(),
                    attendanceHours(employeeId, d),
                    day == null ? null : day.getAttendanceVarianceHours(),
                    day == null ? null : day.getVarianceExplanation(),
                    day == null ? null : day.getEmployeeNote(),
                    day == null ? null : day.getWorkLocation(),
                    day == null ? null : day.getProjectId(),
                    day == null ? null : day.getTaskCode(),
                    qs,
                    findingsByDate.getOrDefault(d, List.of())));
        }

        Map<String, BigDecimal> totals = monthTotals.findByTimesheetIdOrderByCategoryCodeAsc(ts.getId())
                .stream()
                .collect(Collectors.toMap(TimesheetMonthTotal::getCategoryCode,
                        TimesheetMonthTotal::getQuantity, BigDecimal::add, LinkedHashMap::new));

        List<CategoryOption> options = catalog.values().stream()
                .map(c -> new CategoryOption(c.getCode(), c.getName(), c.getUnit(),
                        DayQuantityDeriver.isDerived(c), c.getSource(), c.getMaxPerDay(),
                        c.getDisplayOrder(),
                        c.appliesToTypes().stream().map(Enum::name).toList()))
                .toList();

        boolean submittable = editable
                && findings.stream().noneMatch(TimesheetValidator.Finding::isBlocking);

        return new MonthView(ts.getId(), ts.getPeriodYear(), ts.getPeriodMonth(),
                ts.getStatus().name(), editable, ts.getSubmittedAt(), ts.getSubmittedBy(),
                ts.getEmployeeComment(), dayViews, totals, options,
                monthFindings.stream().distinct().toList(), submittable,
                workLocations(), projectOptions(), intSetting(OVERTIME_ROUNDING_KEY, 30));
    }

    private Map<LocalDate, LeaveRequest> leaveByDate(UUID employeeId, YearMonth ym) {
        Map<LocalDate, LeaveRequest> out = new HashMap<>();
        Map<UUID, LeaveType> typesById = leaveTypes.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, Function.identity(), (a, b) -> a));
        for (LeaveRequest r : leaves.findApprovedOverlapping(employeeId, ym.atDay(1), ym.atEndOfMonth())) {
            // Type is resolved so the UI can label sick vs annual without a second call.
            typesById.get(r.getLeaveTypeId());
            for (LocalDate d = r.getStartDate(); !d.isAfter(r.getEndDate()); d = d.plusDays(1)) {
                if (!d.isBefore(ym.atDay(1)) && !d.isAfter(ym.atEndOfMonth())) {
                    out.putIfAbsent(d, r);
                }
            }
        }
        return out;
    }
}
