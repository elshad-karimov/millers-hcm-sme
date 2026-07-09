package az.millers.hcm.analytics.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.analytics.domain.DashboardLayout;
import az.millers.hcm.analytics.service.DashboardLayoutService;
import az.millers.hcm.analytics.service.KpiValueService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M474 — Analytics endpoints: KPI values and dashboard layouts.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final KpiValueService kpiValueService;
    private final DashboardLayoutService dashboardLayoutService;

    public AnalyticsController(KpiValueService kpiValueService,
                               DashboardLayoutService dashboardLayoutService) {
        this.kpiValueService = kpiValueService;
        this.dashboardLayoutService = dashboardLayoutService;
    }

    /**
     * Get KPI values for the specified codes.
     * Example: GET /api/analytics/kpi-values?codes=HEADCOUNT_ACTIVE,TURNOVER_12M
     */
    @GetMapping("/kpi-values")
    @PreAuthorize(SecurityRoles.READ_HR)
    public Map<String, Object> getKpiValues(@RequestParam List<String> codes) {
        return kpiValueService.values(codes);
    }

    /**
     * List dashboards owned by current user or shared.
     */
    @GetMapping("/dashboards")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<DashboardLayout> listDashboards(@RequestParam(required = false, defaultValue = "false") Boolean all) {
        return all ? dashboardLayoutService.listAll() : dashboardLayoutService.listOwnedOrShared();
    }

    @GetMapping("/dashboards/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public DashboardLayout getDashboard(@PathVariable UUID id) {
        return dashboardLayoutService.get(id);
    }

    @PostMapping("/dashboards")
    @PreAuthorize(SecurityRoles.READ_HR)
    public DashboardLayout createDashboard(@RequestBody DashboardLayout layout) {
        return dashboardLayoutService.create(layout);
    }

    @PutMapping("/dashboards/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public DashboardLayout updateDashboard(@PathVariable UUID id, @RequestBody DashboardLayout layout) {
        return dashboardLayoutService.update(id, layout);
    }

    @DeleteMapping("/dashboards/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public void deleteDashboard(@PathVariable UUID id) {
        dashboardLayoutService.delete(id);
    }
}
