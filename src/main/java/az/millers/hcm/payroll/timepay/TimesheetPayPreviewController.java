package az.millers.hcm.payroll.timepay;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.timepay.TimesheetPayPreviewService.EmployeePreview;
import az.millers.hcm.payroll.timepay.TimesheetPayPreviewService.PeriodPreview;

/**
 * Read-only pricing of approved timesheets, for payroll's eyes only.
 *
 * <p>Every endpoint is a GET and nothing here writes: no payroll run, no result,
 * no payslip. The purpose is to put these numbers next to the January 2026
 * workbook and check them before the engine is allowed to pay anyone.
 *
 * <p>Restricted to payroll and admin roles. Managers and employees never see
 * amounts — that separation is the whole point of slices 1 and 2, where the
 * people who record and approve time are shown quantities only.
 */
@RestController
@RequestMapping("/api/payroll/time-inputs")
@PreAuthorize("hasAnyRole('PAYROLL_SPECIALIST','COMPENSATION_MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
public class TimesheetPayPreviewController {

    private final TimesheetPayPreviewService preview;

    public TimesheetPayPreviewController(TimesheetPayPreviewService preview) {
        this.preview = preview;
    }

    /** The whole period priced — the input side of the workbook. */
    @GetMapping("/{year}/{month}")
    public PeriodPreview period(@PathVariable int year, @PathVariable int month) {
        return preview.period(year, month);
    }

    /** One employee, every input shown next to the amount it produced. */
    @GetMapping("/{year}/{month}/{employeeId}")
    public EmployeePreview employee(@PathVariable int year,
                                    @PathVariable int month,
                                    @PathVariable UUID employeeId) {
        return preview.employee(year, month, employeeId);
    }
}
