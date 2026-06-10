package az.millers.hcm.staffing.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionVarianceDtos.VarianceReport;
import az.millers.hcm.staffing.service.PositionVarianceService;

/** M258 — REST surface for the position variance dashboard (PRD §19). */
@RestController
@RequestMapping("/api/positions/variance")
public class PositionVarianceController {

    private final PositionVarianceService service;

    public PositionVarianceController(PositionVarianceService service) {
        this.service = service;
    }

    /**
     * Returns the variance report for {@code year}/{@code month}. Defaults
     * happen at the SPA layer (current month). HR-admin + payroll-specialist
     * + finance-user — all reasonable consumers of cost variance data.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','PAYROLL_SPECIALIST','FINANCE_USER','HR_SPECIALIST')")
    public VarianceReport variance(@RequestParam int year,
                                    @RequestParam int month) {
        if (month < 1 || month > 12) {
            throw new az.millers.hcm.common.BadRequestException(
                    "month must be 1-12 (was " + month + ")");
        }
        return service.compute(year, month);
    }
}
