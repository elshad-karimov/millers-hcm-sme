package az.millers.hcm.compbenefits.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.service.BenefitCostService;
import az.millers.hcm.compbenefits.service.BenefitCostService.CategorySpend;
import az.millers.hcm.compbenefits.service.BenefitDashboardService;
import az.millers.hcm.security.SecurityRoles;

/** Benefit cost / finance report + dashboard API (HCM_11 M383/M386). */
@RestController
@RequestMapping("/api/compbenefits/reports")
public class BenefitReportController {

    private final BenefitCostService costs;
    private final BenefitDashboardService dashboard;

    public BenefitReportController(BenefitCostService costs, BenefitDashboardService dashboard) {
        this.costs = costs;
        this.dashboard = dashboard;
    }

    /** Benefits dashboard overview (M386). */
    @GetMapping("/dashboard")
    @PreAuthorize(SecurityRoles.READ_BENEFITS)
    public Map<String, Object> dashboard() {
        return dashboard.overview();
    }

    /** Current monthly employer spend + per-category breakdown. */
    @GetMapping("/employer-cost")
    @PreAuthorize(SecurityRoles.READ_BENEFITS)
    public Map<String, Object> employerCost() {
        BigDecimal monthly = costs.currentEmployerMonthlySpend();
        List<CategorySpend> byCategory = costs.employerSpendByCategory();
        return Map.of(
                "employerMonthly", monthly,
                "employerAnnual", monthly.multiply(BigDecimal.valueOf(12)),
                "byCategory", byCategory);
    }
}
