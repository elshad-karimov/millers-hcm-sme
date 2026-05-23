package az.millers.hcm.attendance.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.api.dto.DailySummaryResponse;
import az.millers.hcm.attendance.api.dto.SummaryCorrectionRequest;
import az.millers.hcm.attendance.domain.AttendanceEvent;
import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.EventType;
import az.millers.hcm.attendance.domain.ScheduleAssignment;
import az.millers.hcm.attendance.domain.SummaryStatus;
import az.millers.hcm.attendance.domain.WorkSchedule;
import az.millers.hcm.attendance.repo.AttendanceEventRepository;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.attendance.repo.ScheduleAssignmentRepository;
import az.millers.hcm.attendance.repo.WorkScheduleRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;

/**
 * Attendance engine (PRD 8.4.2 / 8.4.8).
 *
 * <p>Idempotent: re-running for a date upserts the daily summary so the
 * engine is safe to schedule on a cron.
 */
@Service
public class AttendanceEngine {

    private static final String MODULE = "ATTENDANCE";

    private final WorkScheduleRepository schedules;
    private final ScheduleAssignmentRepository assignments;
    private final AttendanceEventRepository events;
    private final DailySummaryRepository summaries;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public AttendanceEngine(WorkScheduleRepository schedules,
                            ScheduleAssignmentRepository assignments,
                            AttendanceEventRepository events,
                            DailySummaryRepository summaries,
                            AuditService audit,
                            CurrentRequest currentRequest) {
        this.schedules = schedules;
        this.assignments = assignments;
        this.events = events;
        this.summaries = summaries;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ---------- Queries ----------

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

    // ---------- Compute ----------

    /** Single employee, single date. Returns null if there's no schedule that day. */
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

        Optional<ScheduleAssignment> activeAssignment = assignments.findActiveOn(employeeId, date);
        if (activeAssignment.isEmpty()) {
            reset(s, SummaryStatus.NO_SCHEDULE);
            return summaries.save(s);
        }
        WorkSchedule sch = schedules.findById(activeAssignment.get().getScheduleId())
                .orElseThrow(() -> new BadRequestException(
                        "Schedule referenced by assignment is missing: "
                                + activeAssignment.get().getScheduleId()));

        s.setScheduleId(sch.getId());
        s.setScheduleStart(sch.getWorkStart());
        s.setScheduleEnd(sch.getWorkEnd());

        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime dayStart = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<AttendanceEvent> dayEvents =
                events.findByEmployeeIdAndEventTimeBetweenOrderByEventTimeAsc(
                        employeeId, dayStart, dayEnd);
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

        DayOfWeek dow = date.getDayOfWeek();
        boolean workingDay = sch.isWorkingDay(dow);

        if (!workingDay) {
            if (firstIn != null && lastOut != null) {
                int total = (int) Duration.between(firstIn, lastOut).toMinutes();
                int over = Math.max(0, total - sch.getBreakMinutes());
                s.setBreakMinutes(sch.getBreakMinutes());
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
            OffsetDateTime schStart = date.atTime(sch.getWorkStart()).atZone(zone).toOffsetDateTime();
            OffsetDateTime schEnd = date.atTime(sch.getWorkEnd()).atZone(zone).toOffsetDateTime();

            int total = (int) Duration.between(firstIn, lastOut).toMinutes();
            int worked = Math.max(0, total - sch.getBreakMinutes());
            s.setBreakMinutes(sch.getBreakMinutes());
            s.setWorkedMinutes(worked);

            long lateRaw = Duration.between(schStart, firstIn).toMinutes();
            s.setLateMinutes((int) Math.max(0, lateRaw - sch.getGracePeriodMinutes()));

            long earlyRaw = Duration.between(lastOut, schEnd).toMinutes();
            s.setEarlyMinutes((int) Math.max(0, earlyRaw));

            long otRaw = Duration.between(schEnd, lastOut).toMinutes();
            s.setOvertimeMinutes((int) Math.max(0, otRaw));

            s.setStatus(SummaryStatus.PRESENT);
        } else {
            // Only one of IN/OUT is present.
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
     * Sweep all employees that have a schedule assignment overlapping the range
     * and (re)compute their daily summaries.
     */
    @Transactional
    public RunResult run(LocalDate from, LocalDate to, UUID onlyEmployeeId) {
        if (to.isBefore(from)) {
            throw new BadRequestException("toDate cannot be before fromDate");
        }
        java.util.Set<UUID> processedEmployees = new java.util.HashSet<>();
        int written = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<UUID> employeeIds = onlyEmployeeId != null
                    ? List.of(onlyEmployeeId)
                    : assignments.findEmployeesWithScheduleOn(d);
            for (UUID empId : employeeIds) {
                computeFor(empId, d);
                processedEmployees.add(empId);
                written++;
            }
        }
        return new RunResult(processedEmployees.size(), written);
    }

    // ---------- Manual correction ----------

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
