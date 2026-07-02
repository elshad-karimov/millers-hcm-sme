package az.millers.hcm.compensation.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.api.dto.CompensationDashboardDto;
import az.millers.hcm.compensation.api.dto.CompensationDashboardDto.BudgetUtilizationRow;
import az.millers.hcm.compensation.api.dto.OutOfBandReportRow;
import az.millers.hcm.compensation.service.CompensationDashboardService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M370 — Compensation dashboard + analytics endpoints.
 * Reuses existing services for read-only aggregations.
 */
@RestController
@RequestMapping("/api/compensation")
public class CompensationDashboardController {

    private final CompensationDashboardService dashboardService;

    public CompensationDashboardController(CompensationDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public ResponseEntity<CompensationDashboardDto> dashboard() {
        return ResponseEntity.ok(dashboardService.dashboard());
    }

    @GetMapping("/reports/compa-ratio-distribution")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public ResponseEntity<Map<String, Integer>> compaRatioDistribution(
            @RequestParam(required = false) UUID gradeId) {
        return ResponseEntity.ok(dashboardService.compaRatioDistribution(gradeId));
    }

    @GetMapping("/reports/out-of-band")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public ResponseEntity<List<OutOfBandReportRow>> outOfBandReport() {
        return ResponseEntity.ok(dashboardService.outOfBandReport());
    }

    @GetMapping("/reports/budget-vs-actual")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public ResponseEntity<List<BudgetUtilizationRow>> budgetVsActual() {
        return ResponseEntity.ok(dashboardService.budgetVsActual());
    }
}
