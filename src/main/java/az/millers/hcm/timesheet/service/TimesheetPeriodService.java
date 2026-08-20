package az.millers.hcm.timesheet.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ControlBoard;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ControlRow;
import az.millers.hcm.timesheet.domain.PeriodStatus;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetPeriodControl;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.event.TimesheetPeriodLockedEvent;
import az.millers.hcm.timesheet.repo.TimesheetPeriodControlRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * HR's view of a whole period, and the lock that closes it.
 *
 * <p>The lock is the gate payroll waits behind. It is refused while any
 * timesheet in the period is still SUBMITTED or RETURNED, because locking a
 * period with undecided months is precisely how unapproved hours reach payroll
 * — the failure this whole pipeline exists to prevent.
 *
 * <p>Contains no monetary logic. "Payroll ready" here means *decided and
 * closed*, not *priced*.
 */
@Service
public class TimesheetPeriodService {

    private static final String MODULE = "TIMESHEET";
    private static final String ENTITY = "TimesheetPeriod";

    /** Statuses that must be resolved before a period can close. */
    private static final Set<TimesheetStatus> UNDECIDED =
            Set.of(TimesheetStatus.SUBMITTED, TimesheetStatus.RETURNED);

    private final TimesheetPeriodControlRepository controls;
    private final TimesheetRepository timesheets;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final ApplicationEventPublisher events;

    public TimesheetPeriodService(TimesheetPeriodControlRepository controls,
                                  TimesheetRepository timesheets,
                                  EmployeeRepository employees,
                                  AuditService audit,
                                  CurrentRequest currentRequest,
                                  ApplicationEventPublisher events) {
        this.controls = controls;
        this.timesheets = timesheets;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.events = events;
    }

    /** Whether the period refuses changes. Consulted by entry and approval. */
    @Transactional(readOnly = true)
    public boolean isLocked(int year, int month) {
        return controls.findByPeriodYearAndPeriodMonth(year, month)
                .map(TimesheetPeriodControl::isLocked)
                .orElse(false);
    }

    /** Refuse the caller's action when the period is closed. */
    public void assertOpen(int year, int month) {
        if (isLocked(year, month)) {
            throw new BadRequestException("Period " + year + "-" + String.format("%02d", month)
                    + " is locked. Ask HR to unlock it, or raise a correction request.");
        }
    }

    // ---------- Control board ----------

    @Transactional(readOnly = true)
    public ControlBoard board(int year, int month) {
        List<Timesheet> all = timesheets.findByPeriodYearAndPeriodMonthOrderByEmployeeIdAsc(year, month);
        Map<UUID, Employee> byId = employees.findAllById(
                        all.stream().map(Timesheet::getEmployeeId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));

        Map<TimesheetStatus, Long> counts = all.stream()
                .collect(Collectors.groupingBy(Timesheet::getStatus, Collectors.counting()));

        TimesheetPeriodControl control = controls.findByPeriodYearAndPeriodMonth(year, month)
                .orElseGet(() -> new TimesheetPeriodControl(year, month));

        long undecided = UNDECIDED.stream().mapToLong(s -> counts.getOrDefault(s, 0L)).sum();
        boolean locked = control.isLocked();
        String blocked = locked ? "Period is already locked."
                : undecided > 0
                        ? undecided + " timesheet(s) are still awaiting a decision."
                        : all.isEmpty() ? "No timesheets in this period yet." : null;

        List<ControlRow> rows = all.stream()
                .map(t -> toRow(t, byId.get(t.getEmployeeId())))
                .toList();

        return new ControlBoard(
                year, month,
                control.getStatus().name(),
                control.getLockedAt(),
                control.getLockedBy(),
                all.size(),
                counts.getOrDefault(TimesheetStatus.DRAFT, 0L).intValue(),
                counts.getOrDefault(TimesheetStatus.SUBMITTED, 0L).intValue(),
                counts.getOrDefault(TimesheetStatus.RETURNED, 0L).intValue(),
                counts.getOrDefault(TimesheetStatus.APPROVED, 0L).intValue(),
                counts.getOrDefault(TimesheetStatus.LOCKED, 0L).intValue(),
                (int) rows.stream().filter(ControlRow::payrollReady).count(),
                blocked == null,
                blocked,
                rows);
    }

    private ControlRow toRow(Timesheet t, Employee e) {
        boolean ready = t.getStatus() == TimesheetStatus.APPROVED
                || t.getStatus() == TimesheetStatus.LOCKED;
        String exception = switch (t.getStatus()) {
            case SUBMITTED -> "Awaiting manager approval";
            case RETURNED -> "Returned to employee for correction";
            case DRAFT -> "Not submitted";
            case REOPENED -> "Reopened after approval";
            default -> null;
        };
        int warnings = t.getValidationWarnings() == null || t.getValidationWarnings().isBlank()
                ? 0 : (int) t.getValidationWarnings().lines().filter(l -> !l.isBlank()).count();

        return new ControlRow(
                t.getId(),
                t.getEmployeeId(),
                e == null ? null : e.getEmployeeNo(),
                e == null ? null : e.getLastName() + ", " + e.getFirstName(),
                t.getStatus().name(),
                t.getTotalWorkedHours() == null ? BigDecimal.ZERO : t.getTotalWorkedHours(),
                warnings,
                0,
                exception,
                ready);
    }

    // ---------- Lock / unlock ----------

    @Transactional
    public ControlBoard lock(int year, int month, String reason) {
        if (timesheets.existsByPeriodYearAndPeriodMonthAndStatusIn(year, month, UNDECIDED)) {
            throw new BadRequestException(
                    "Cannot lock: some timesheets are still submitted or returned. "
                            + "Every month must be approved or left as a draft before the "
                            + "period closes.");
        }
        TimesheetPeriodControl control = controls.findByPeriodYearAndPeriodMonth(year, month)
                .orElseGet(() -> new TimesheetPeriodControl(year, month));
        if (control.isLocked()) {
            throw new BadRequestException("Period is already locked.");
        }

        control.setStatus(PeriodStatus.LOCKED);
        control.setLockedAt(OffsetDateTime.now());
        control.setLockedBy(currentRequest.username());
        control.setLockReason(reason);
        controls.save(control);

        // Approved months become LOCKED so nothing further can move them.
        List<Timesheet> approved = timesheets.findByPeriodYearAndPeriodMonthAndStatus(
                year, month, TimesheetStatus.APPROVED);
        for (Timesheet t : approved) {
            t.setStatus(TimesheetStatus.LOCKED);
            t.setLockedAt(OffsetDateTime.now());
        }
        timesheets.saveAll(approved);

        audit.record(MODULE, ENTITY, year + "-" + month, "PERIOD_LOCK", null,
                Map.of("reason", reason == null ? "" : reason, "locked", approved.size()));

        // The lock is the moment downstream modules may safely read the month.
        // Published rather than called so timesheet stays unaware of payroll;
        // listeners run after commit, so a failed listener cannot undo a lock.
        events.publishEvent(new TimesheetPeriodLockedEvent(year, month,
                approved.stream().map(Timesheet::getId).toList(),
                currentRequest.username()));

        return board(year, month);
    }

    @Transactional
    public ControlBoard unlock(int year, int month, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException(
                    "A reason is required to unlock a closed period — it is an auditable event.");
        }
        TimesheetPeriodControl control = controls.findByPeriodYearAndPeriodMonth(year, month)
                .orElseThrow(() -> new BadRequestException("Period is not locked."));
        if (!control.isLocked()) {
            throw new BadRequestException("Period is not locked.");
        }

        control.setStatus(PeriodStatus.OPEN);
        control.setUnlockedAt(OffsetDateTime.now());
        control.setUnlockedBy(currentRequest.username());
        control.setUnlockReason(reason);
        controls.save(control);

        // LOCKED months go back to APPROVED — not to DRAFT. Unlocking a period
        // re-opens HR's ability to act; it does not un-approve anyone's month.
        List<Timesheet> locked = timesheets.findByPeriodYearAndPeriodMonthAndStatus(
                year, month, TimesheetStatus.LOCKED);
        for (Timesheet t : locked) {
            t.setStatus(TimesheetStatus.APPROVED);
            t.setLockedAt(null);
        }
        timesheets.saveAll(locked);

        audit.record(MODULE, ENTITY, year + "-" + month, "PERIOD_UNLOCK", null,
                Map.of("reason", reason, "reopened", locked.size()));
        return board(year, month);
    }
}
