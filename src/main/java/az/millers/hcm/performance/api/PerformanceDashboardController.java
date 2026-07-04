package az.millers.hcm.performance.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.service.PerformanceDashboardService;
import az.millers.hcm.security.SecurityRoles;

/** HR performance dashboard (HCM_12 M401, PRD §27.1). */
@RestController
@RequestMapping("/api/performance/dashboard")
public class PerformanceDashboardController {

    private final PerformanceDashboardService service;

    public PerformanceDashboardController(PerformanceDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PerformanceDashboardService.DashboardResponse overview(@RequestParam UUID cycleId) {
        return service.overview(cycleId);
    }
}
