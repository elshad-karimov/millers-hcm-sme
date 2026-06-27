package az.millers.hcm.attendance.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.api.dto.DailySummaryResponse;
import az.millers.hcm.attendance.api.dto.SummaryCorrectionRequest;
import az.millers.hcm.attendance.domain.AttendanceEvent;
import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.EventType;
import az.millers.hcm.attendance.domain.RosterEntry;
import az.millers.hcm.attendance.domain.ScheduleAssignment;
import az.millers.hcm.attendance.domain.Shift;
import az.millers.hcm.attendance.domain.SummaryStatus;
import az.millers.hcm.attendance.domain.WorkSchedule;
import az.millers.hcm.attendance.repo.AttendanceEventRepository;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.attendance.repo.RosterEntryRepository;
import az.millers.hcm.attendance.repo.ScheduleAssignmentRepository;
import az.millers.hcm.attendance.repo.ShiftRepository;
import az.millers.hcm.attendance.repo.WorkScheduleRepository;
import az.millers.hcm.attendance.domain.AttendancePolicy;
import az.millers.hcm.attendance.service.AttendancePolicyService;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;

/**
 * Attendance engine (PRD 8.4.2 / 8.4.8; M112 — roster-aware).
 *
 * <p>For each (employee, work date) pair the engine resolves a
 * <em>scheduled window</em> from one of three sources, in priority order:
 * <ol>
 *   <li>A rostered {@link Shift} for that exact date (M110/M111). Wins
 *       whenever present — the roster is the explicit "this is the work
 *       window for this human on this day" decision.</li>
 *   <li>The legacy {@link WorkSchedule} template referenced by an active
 *       {@link ScheduleAssignment}. The pre-M110 path.</li>
 *   <li>No source — summary status becomes {@code NO_SCHEDULE}.</li>
 * </ol>
 *
 * <p>The math (late, early, overtime, worked minutes) is the same regardless
 * of source; only the window changes. Cross-midnight shifts (a Night shift
 * 22:00 → 06:00) are handled by extending the {@link OffsetDateTime} window
 * across the day boundary.
 *
 * <p>Idempotent: re-running for a date upserts the summary so the engine is
 * safe to schedule on a cron.
 */
@Service
public class AttendanceEngine {

    private static final String MODULE = "ATTENDANCE";

    /** Slack on each side of the scheduled window when fetching events. */
    private static final Duration EVENT_FETCH_SLACK = Duration.ofHours(4);

    private final WorkScheduleRepository schedules;
    private final ScheduleAssignmentRepository assignments;
    private final AttendanceEventRepository events;
    private final DailySummaryRepository summaries;
    private final RosterEntryRepository roster;
    private final ShiftRepository shifts;
    private final AttendancePolicyService policies;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public AttendanceEngine(WorkScheduleRepository schedules,
                            ScheduleAssignmentRepository assignments,
                            AttendanceEventRepository events,
                            DailySummaryRepository summaries,
                            RosterEntryRepository roster,
                            ShiftRepository shifts,
                            AttendancePolicyService policies,
                            AuditService audit,
                            CurrentRequest currentRequest) {
        this.schedules = schedules;
        this.assignments = assignments;
        this.events = events;
        this.summaries = summaries;
        this.roster = roster;
        this.shifts = shifts;
        this.policies = policies;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ─── Queries ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DailySummary> listByRange(LocalDate from, LocalDate to) {
        return summaries.findByWorkDateBetweenOrderByWorkDateAscEmployeeIdAsc(from, to);
    }

    @Transactional(readOnly = true)
    public List<DailySummary> listForEmployee(UUID employeeId, LocalDate from, LocalDate to) {
        return summaries.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to);
    }

    @Transactional(readOnly = true)
    public DailySummary get(UUID id) {
        return summaries.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Daily summary not found: " + id));
    }

    // ─── Compute ────────────────────────────────────────────────────────

    /**
     * Resolved scheduled window for a (employee, date) pair, plus the
     * source metadata. Package-private so tests can construct one without
     * needing the full repository fetch.
     */
    record Resolution(
            String source,          // "ROSTER" | "SCHEDULE" | "NONE"
            UUID scheduleId,        // null when source != SCHEDULE
            UUID shiftId,           // null when source != ROSTER
            ScheduleMath.Window window,
            int breakMinutes,
            int graceMinutes,
            boolean workingDay,
            boolean crossesMidnight) {

        static Resolution none() {
            return new Resolution("NONE", null, null, null, 0, 0, false, false);
        }
    }

    @Transactional
    public DailySummary computeFor(UUID employeeId, LocalDate date) {
        DailySummary s = summaries.findByEmployeeIdAndWorkDate(employeeId, date)
                .orElseGet(() -> {
                    DailySummary n = new DailySummary();
                    n.setEmployeeId(employeeId);
                    n.setWorkDate(date);
                    return n;
                });
        if (s.getCorrectedAt() != null) {
            // Manual correction wins — engine never overwrites it.
            return s;
        }

        // Resolve applicable attendance policy (M326): policy grace overrides schedule grace.
        AttendancePolicy policy = policies.findForEmployee(employeeId).orElse(null);
        if (policy != null) {
            s.setPolicyId(policy.getId());
        }

        Resolution res = resolve(employeeId, date, policy);
        s.setSource(res.source());
        s.setScheduleId(res.scheduleId());
        s.setShiftId(res.shiftId());

        if ("NONE".equals(res.source())) {
            s.setScheduleStart(null);
            s.setScheduleEnd(null);
            reset(s, SummaryStatus.NO_SCHEDULE);
            return summaries.save(s);
        }

        s.setScheduleStart(res.window().start().toLocalTime());
        s.setScheduleEnd(res.window().end().toLocalTime());

        // Event fetch window: shift window ± 4h slack. For overnight shifts
        // this is the only way to see the morning clock-out.
        ScheduleMath.Window eventWin =
                ScheduleMath.eventFetchWindow(res.window(), EVENT_FETCH_SLACK);
        List<AttendanceEvent> dayEvents =
                events.findByEmployeeIdAndEventTimeBetweenOrderByEventTimeAsc(
                        employeeId, eventWin.start(), eventWin.end());
        s.setRawEventCount(dayEvents.size());

        OffsetDateTime firstIn = dayEvents.stream()
                .filter(e -> e.getEventType() == EventType.IN)
                .map(AttendanceEvent::getEventTime)
                .min(java.util.Comparator.naturalOrder())
                .orElse(null);
        OffsetDateTime lastOut = dayEvents.stream()
                .filter(e -> e.getEventType() == EventType.OUT)
                .map(AttendanceEvent::getEventTime)
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);
        s.setEntryTime(firstIn);
        s.setExitTime(lastOut);

        if (!res.workingDay()) {
            // Legacy semantics: weekend with both IN+OUT becomes pure OT.
            if (firstIn != null && lastOut != null) {
                int total = (int) Duration.between(firstIn, lastOut).toMinutes();
                int over = Math.max(0, total - res.breakMinutes());
                s.setBreakMinutes(res.breakMinutes());
                s.setWorkedMinutes(0);
                s.setLateMinutes(0);
                s.setEarlyMinutes(0);
                s.setOvertimeMinutes(over);
                s.setStatus(SummaryStatus.PRESENT);
            } else {
                reset(s, SummaryStatus.NON_WORKING_DAY);
            }
        } else if (firstIn == null && lastOut == null) {
            reset(s, SummaryStatus.ABSENT);
        } else if (firstIn != null && lastOut != null) {
            ScheduleMath.Metrics m = ScheduleMath.metrics(
                    res.window().start(), res.window().end(),
                    firstIn, lastOut,
                    res.breakMinutes(), res.graceMinutes());
            s.setBreakMinutes(res.breakMinutes());
            s.setWorkedMinutes(m.workedMinutes());
            s.setLateMinutes(m.lateMinutes());
            s.setEarlyMinutes(m.earlyMinutes());
            s.setOvertimeMinutes(m.overtimeMinutes());
            s.setStatus(SummaryStatus.PRESENT);
        } else {
            // Only one of IN/OUT present.
            s.setBreakMinutes(0);
            s.setWorkedMinutes(0);
            s.setLateMinutes(0);
            s.setEarlyMinutes(0);
            s.setOvertimeMinutes(0);
            s.setStatus(SummaryStatus.PARTIAL);
        }

        s.setComputedAt(OffsetDateTime.now());
        return summaries.save(s);
    }

    /**
     * Decide which schedule source applies to (employee, date). Roster wins
     * when an entry exists; otherwise we fall through to the legacy
     * WorkSchedule path. Policy grace overrides schedule grace when present.
     */
    private Resolution resolve(UUID employeeId, LocalDate date, AttendancePolicy policy) {
        ZoneId zone = ZoneId.systemDefault();

        Optional<RosterEntry> entryOpt = roster.findByEmployeeIdAndRosterDate(employeeId, date);
        if (entryOpt.isPresent()) {
            RosterEntry entry = entryOpt.get();
            Shift shift = shifts.findById(entry.getShiftId())
                    .orElseThrow(() -> new BadRequestException(
                            "Shift referenced by roster entry is missing: " + entry.getShiftId()));
            ScheduleMath.Window window = ScheduleMath.window(
                    date, shift.getStartTime(), shift.getEndTime(),
                    shift.isCrossesMidnight(), zone);
            int rosterGrace = policy != null ? policy.getClockInGraceMinutes() : 0;
            return new Resolution(
                    "ROSTER", null, shift.getId(),
                    window,
                    shift.getBreakMinutes(),
                    rosterGrace,
                    true,        // a rostered day is by definition a working day
                    shift.isCrossesMidnight());
        }

        Optional<ScheduleAssignment> activeAssignment = assignments.findActiveOn(employeeId, date);
        if (activeAssignment.isEmpty()) {
            return Resolution.none();
        }
        WorkSchedule sch = schedules.findById(activeAssignment.get().getScheduleId())
                .orElseThrow(() -> new BadRequestException(
                        "Schedule referenced by assignment is missing: "
                                + activeAssignment.get().getScheduleId()));

        DayOfWeek dow = date.getDayOfWeek();
        boolean workingDay = sch.isWorkingDay(dow);
        // WorkSchedule doesn't model cross-midnight on individual days,
        // so we treat it as a regular within-day window.
        ScheduleMath.Window window = ScheduleMath.window(
                date, sch.getWorkStart(), sch.getWorkEnd(), false, zone);
        int schedGrace = policy != null ? policy.getClockInGraceMinutes() : sch.getGracePeriodMinutes();
        return new Resolution(
                "SCHEDULE", sch.getId(), null,
                window,
                sch.getBreakMinutes(),
                schedGrace,
                workingDay,
                false);
    }

    /**
     * Sweep all employees that have a schedule assignment or roster entry
     * overlapping the range and (re)compute their daily summaries.
     */
    @Transactional
    public RunResult run(LocalDate from, LocalDate to, UUID onlyEmployeeId) {
        if (to.isBefore(from)) {
            throw new BadRequestException("toDate cannot be before fromDate");
        }
        Set<UUID> processedEmployees = new HashSet<>();
        int written = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Set<UUID> employeesForDay;
            if (onlyEmployeeId != null) {
                employeesForDay = Set.of(onlyEmployeeId);
            } else {
                employeesForDay = new HashSet<>();
                employeesForDay.addAll(assignments.findEmployeesWithScheduleOn(d));
                // Pull in anyone who has a rostered shift that day but no
                // schedule assignment (M110-only teams).
                for (RosterEntry r : roster
                        .findByRosterDateBetweenOrderByRosterDateAscEmployeeIdAsc(d, d)) {
                    employeesForDay.add(r.getEmployeeId());
                }
            }
            for (UUID empId : employeesForDay) {
                computeFor(empId, d);
                processedEmployees.add(empId);
                written++;
            }
        }
        return new RunResult(processedEmployees.size(), written);
    }

    // ─── Manual correction ──────────────────────────────────────────────

    @Transactional
    public DailySummary applyCorrection(UUID summaryId, SummaryCorrectionRequest req) {
        DailySummary s = get(summaryId);
        DailySummaryResponse before = DailySummaryResponse.from(s);
        if (req.workedMinutes() != null) s.setWorkedMinutes(req.workedMinutes());
        if (req.lateMinutes() != null) s.setLateMinutes(req.lateMinutes());
        if (req.earlyMinutes() != null) s.setEarlyMinutes(req.earlyMinutes());
        if (req.breakMinutes() != null) s.setBreakMinutes(req.breakMinutes());
        if (req.overtimeMinutes() != null) s.setOvertimeMinutes(req.overtimeMinutes());
        if (req.status() != null) s.setStatus(req.status());
        s.setCorrectionReason(req.reason());
        s.setCorrectedBy(currentRequest.username());
        s.setCorrectedAt(OffsetDateTime.now());
        DailySummary saved = summaries.save(s);
        audit.record(MODULE, "DailySummary", summaryId.toString(),
                "MANUAL_CORRECTION", before, DailySummaryResponse.from(saved));
        return saved;
    }

    private void reset(DailySummary s, SummaryStatus status) {
        s.setStatus(status);
        s.setWorkedMinutes(0);
        s.setLateMinutes(0);
        s.setEarlyMinutes(0);
        s.setOvertimeMinutes(0);
        s.setBreakMinutes(0);
        s.setComputedAt(OffsetDateTime.now());
    }

    public record RunResult(int employeesProcessed, int summariesWritten) {
    }
}
