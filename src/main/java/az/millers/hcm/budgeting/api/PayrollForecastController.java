package az.millers.hcm.budgeting.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.budgeting.api.dto.MonthlyForecast;
import az.millers.hcm.budgeting.service.PayrollForecastService;
import az.millers.hcm.security.SecurityRoles;

/**
 * HCM_20 M426 — Payroll cost forecast REST API.
 * HR/Finance confidential.
 */
@RestController
@RequestMapping("/api/budgets/forecast")
public class PayrollForecastController {

    private final PayrollForecastService service;

    public PayrollForecastController(PayrollForecastService service) {
        this.service = service;
    }

    @GetMapping("/payroll")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<MonthlyForecast> payrollForecast(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) BigDecimal growthPct) {
        return service.projectMonthlyCost(months, growthPct);
    }
}
