package az.millers.hcm.payroll.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.profile.ProfilePayPreviewService.EmployeePreview;
import az.millers.hcm.payroll.profile.ProfilePayPreviewService.PeriodPreview;

/**
 * Read-only, profile-aware pricing — for payroll's eyes only.
 *
 * <p>Every endpoint is a GET and nothing here writes: no payroll run, no result,
 * no payslip, no settlement. The purpose is to put these numbers next to the
 * company's spreadsheets and check all four contract types before the engine is
 * allowed to pay anyone.
 *
 * <p>Restricted to payroll and admin roles. Managers and employees never see
 * amounts: the people who record and approve time are shown quantities only,
 * and excess hours are calculated for them rather than entered by them.
 */
@RestController
@RequestMapping("/api/payroll/calculation-profiles")
@PreAuthorize("hasAnyRole('PAYROLL_SPECIALIST','COMPENSATION_MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
public class ProfilePayPreviewController {

    private final ProfilePayPreviewService preview;
    private final CalculationProfileRepository profiles;

    public ProfilePayPreviewController(ProfilePayPreviewService preview,
                                       CalculationProfileRepository profiles) {
        this.preview = preview;
        this.profiles = profiles;
    }

    /** The configured profiles, including which of their settings are unresolved. */
    @GetMapping
    public List<CalculationProfile> profiles() {
        return profiles.findByActiveTrueOrderByCodeAsc();
    }

    /** A whole period priced by profile. */
    @GetMapping("/preview/{year}/{month}")
    public PeriodPreview period(@PathVariable int year, @PathVariable int month) {
        return preview.period(year, month);
    }

    /** One employee, every input shown next to the amount it produced. */
    @GetMapping("/preview/{year}/{month}/{employeeId}")
    public EmployeePreview employee(@PathVariable int year,
                                    @PathVariable int month,
                                    @PathVariable UUID employeeId) {
        return preview.employee(year, month, employeeId);
    }

    /** The month-by-month ledger behind a rotation employee's settlement. */
    @GetMapping("/excess-ledger/{employeeId}")
    public List<ExcessAccumulatorService.Ledger> excessLedger(@PathVariable UUID employeeId) {
        return preview.excessLedger(employeeId);
    }
}
