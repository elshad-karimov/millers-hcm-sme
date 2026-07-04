package az.millers.hcm.manager.api;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.manager.service.ManagerAnalyticsService;

/**
 * M434 — Manager analytics: team metrics scoped to the current manager's direct reports.
 */
@RestController
@RequestMapping("/api/manager/analytics")
public class ManagerAnalyticsController {

    private final ManagerAnalyticsService service;

    public ManagerAnalyticsController(ManagerAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN')")
    public Map<String, Object> analytics() {
        return service.analytics();
    }
}
