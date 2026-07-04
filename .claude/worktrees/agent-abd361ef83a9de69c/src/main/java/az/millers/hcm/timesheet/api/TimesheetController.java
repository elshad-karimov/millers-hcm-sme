package az.millers.hcm.timesheet.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.timesheet.api.dto.DayCorrectionRequest;
import az.millers.hcm.timesheet.api.dto.GenerateTimesheetRequest;
import az.millers.hcm.timesheet.api.dto.TimesheetDayResponse;
import az.millers.hcm.timesheet.api.dto.TimesheetResponse;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.service.TimesheetService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/timesheets")
public class TimesheetController {

    private final TimesheetService service;

    public TimesheetController(TimesheetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','PAYROLL_SPECIALIST','FINANCE_USER','DEPARTMENT_MANAGER')")
    public List<TimesheetResponse> listForPeriod(
            @RequestParam int year,
            @RequestParam int month) {
        List<Timesheet> rows = service.listForPeriod(year, month);
        return rows.stream().map(t -> TimesheetResponse.from(t, List.of())).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public TimesheetResponse get(@PathVariable UUID id) {
        Timesheet ts = service.get(id);
        List<TimesheetDayResponse> dayList = service.daysOf(id).stream()
                .map(TimesheetDayResponse::from).toList();
        return TimesheetResponse.from(ts, dayList);
    }

    @PostMapping("/generate")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TimesheetResponse generate(@Valid @RequestBody GenerateTimesheetRequest req) {
        Timesheet ts = service.generate(req.employeeId(), req.periodYear(), req.periodMonth());
        List<TimesheetDayResponse> dayList = service.daysOf(ts.getId()).stream()
                .map(TimesheetDayResponse::from).toList();
        return TimesheetResponse.from(ts, dayList);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public TimesheetResponse submit(@PathVariable UUID id) {
        Timesheet ts = service.submit(id);
        List<TimesheetDayResponse> dayList = service.daysOf(id).stream()
                .map(TimesheetDayResponse::from).toList();
        return TimesheetResponse.from(ts, dayList);
    }

    @PostMapping("/{id}/days/{dayId}/correct")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public TimesheetDayResponse correctDay(@PathVariable UUID id,
                                            @PathVariable UUID dayId,
                                            @Valid @RequestBody DayCorrectionRequest req) {
        return TimesheetDayResponse.from(service.correctDay(id, dayId, req));
    }

    /**
     * M217 — HR can recall a submitted timesheet back to DRAFT so the employee
     * can re-edit it before re-submitting. Delegates to onCancelled() which
     * performs the SUBMITTED → DRAFT transition.
     */
    @PostMapping("/{id}/recall")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public TimesheetResponse recall(@PathVariable UUID id,
                                     @RequestParam(required = false) String reason) {
        Timesheet ts = service.onCancelled(id, reason);
        List<TimesheetDayResponse> dayList = service.daysOf(id).stream()
                .map(TimesheetDayResponse::from).toList();
        return TimesheetResponse.from(ts, dayList);
    }
}
