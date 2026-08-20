package az.millers.hcm.timesheet.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ControlBoard;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.LockRequest;
import az.millers.hcm.timesheet.service.TimesheetPeriodService;

/**
 * HR's period control: the whole month at a glance, and the lock that closes it.
 *
 * <p>Locking is restricted to HR and system admins because it is the gate
 * payroll waits behind — a manager closing a period would be deciding for the
 * whole organisation, not their team.
 */
@RestController
@RequestMapping("/api/timesheets/control")
@PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
public class TimesheetPeriodController {

    private final TimesheetPeriodService periods;

    public TimesheetPeriodController(TimesheetPeriodService periods) {
        this.periods = periods;
    }

    @GetMapping("/{year}/{month}")
    public ControlBoard board(@PathVariable int year, @PathVariable int month) {
        return periods.board(year, month);
    }

    /** Refused while any timesheet in the period is still submitted or returned. */
    @PostMapping("/{year}/{month}/lock")
    public ControlBoard lock(@PathVariable int year, @PathVariable int month,
                             @RequestBody(required = false) LockRequest req) {
        return periods.lock(year, month, req == null ? null : req.reason());
    }

    @PostMapping("/{year}/{month}/unlock")
    public ControlBoard unlock(@PathVariable int year, @PathVariable int month,
                               @RequestBody LockRequest req) {
        return periods.unlock(year, month, req == null ? null : req.reason());
    }
}
