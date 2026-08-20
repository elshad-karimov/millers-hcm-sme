package az.millers.hcm.timesheet.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.selfservice.service.EmployeeContextService;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.CorrectionRequestInput;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.CorrectionView;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.BulkDayEntryRequest;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.DayEntryRequest;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.MonthView;
import az.millers.hcm.timesheet.api.dto.DailyEntryDtos.SubmitRequest;
import az.millers.hcm.timesheet.service.TimesheetCorrectionService;
import az.millers.hcm.timesheet.service.TimesheetEntryService;

/**
 * The employee's own timesheet.
 *
 * <p>No endpoint takes an employee id: the subject is always the caller,
 * resolved from their token via {@link EmployeeContextService}. That removes
 * the whole class of "employee guesses another employee's id" bugs rather than
 * relying on every method to remember a scope check.
 *
 * <p>Nothing here returns a monetary value. Rates, amounts and net pay belong
 * to payroll and are not reachable from this controller.
 */
@RestController
@RequestMapping("/api/self/timesheets")
public class EmployeeSelfTimesheetController {

    private final TimesheetEntryService entryService;
    private final TimesheetCorrectionService corrections;
    private final EmployeeContextService employeeContext;

    public EmployeeSelfTimesheetController(TimesheetEntryService entryService,
                                           TimesheetCorrectionService corrections,
                                           EmployeeContextService employeeContext) {
        this.entryService = entryService;
        this.corrections = corrections;
        this.employeeContext = employeeContext;
    }

    /** The caller's month — created in DRAFT on first open. */
    @GetMapping("/{year}/{month}")
    @PreAuthorize("isAuthenticated()")
    public MonthView month(@PathVariable int year, @PathVariable int month) {
        return entryService.openMonth(me(), year, month);
    }

    /** Record or replace one day. */
    @PutMapping("/{year}/{month}/days/{date}")
    @PreAuthorize("isAuthenticated()")
    public MonthView saveDay(@PathVariable int year,
                             @PathVariable int month,
                             @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestBody DayEntryRequest req) {
        return entryService.saveDay(me(), year, month, date, req);
    }

    /**
     * Record or replace MANY days in one call.
     *
     * <p>What the grid saves with. A month of offshore rotation is ~20 edited
     * days; one request per day would be 20 round-trips, 20 chances to half-save,
     * and 20 recomputes of the month totals. This applies them in a single
     * transaction — all days land or none do — and returns the month once.
     *
     * <p>A day with a null {@code entry} is cleared, so the grid can blank a
     * row without a separate DELETE.
     */
    @PutMapping("/{year}/{month}/days")
    @PreAuthorize("isAuthenticated()")
    public MonthView saveDays(@PathVariable int year,
                              @PathVariable int month,
                              @RequestBody BulkDayEntryRequest req) {
        return entryService.saveDays(me(), year, month, req);
    }

    /** Copy the nearest earlier completed day onto this one. */
    @PostMapping("/{year}/{month}/days/{date}/copy-previous")
    @PreAuthorize("isAuthenticated()")
    public MonthView copyPrevious(@PathVariable int year,
                                  @PathVariable int month,
                                  @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return entryService.copyPreviousDay(me(), year, month, date);
    }

    /** Clear a day back to "not entered". */
    @DeleteMapping("/{year}/{month}/days/{date}")
    @PreAuthorize("isAuthenticated()")
    public MonthView clearDay(@PathVariable int year,
                              @PathVariable int month,
                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return entryService.clearDay(me(), year, month, date);
    }

    /** Validate and submit the month for approval. */
    @PostMapping("/{year}/{month}/submit")
    @PreAuthorize("isAuthenticated()")
    public MonthView submit(@PathVariable int year,
                            @PathVariable int month,
                            @RequestBody SubmitRequest req) {
        return entryService.submit(me(), year, month, req);
    }

    /** Pull back a submission that has not been approved yet. */
    @PostMapping("/{year}/{month}/recall")
    @PreAuthorize("isAuthenticated()")
    public MonthView recall(@PathVariable int year, @PathVariable int month) {
        return entryService.recall(me(), year, month);
    }

    /**
     * Ask for a change to a day in a month that is already approved or locked.
     *
     * <p>A settled month is never edited in place — that would destroy the
     * record of what was approved.
     */
    @PostMapping("/{year}/{month}/corrections")
    @PreAuthorize("isAuthenticated()")
    public CorrectionView requestCorrection(@PathVariable int year,
                                            @PathVariable int month,
                                            @RequestBody CorrectionRequestInput req) {
        return corrections.request(me(), year, month, req);
    }

    /** The caller's own correction requests and where they stand. */
    @GetMapping("/corrections")
    @PreAuthorize("isAuthenticated()")
    public List<CorrectionView> myCorrections() {
        return corrections.mine(me());
    }

    private UUID me() {
        return employeeContext.currentEmployee().getId();
    }
}
