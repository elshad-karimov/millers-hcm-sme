package az.millers.hcm.performance.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.service.CareerDashboardService;
import az.millers.hcm.performance.service.CareerDashboardService.DashboardResponse;

/**
 * HCM_16 M418 — career dashboard (PRD §16.8).
 * Employee self-service: interests + dev plans + mentoring + paths + job recs.
 */
@RestController
@RequestMapping("/api/talent/career-dashboard")
public class CareerDashboardController {

    private final CareerDashboardService service;

    public CareerDashboardController(CareerDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public DashboardResponse dashboard(@RequestParam UUID employeeId) {
        return service.dashboard(employeeId);
    }
}
