package az.millers.hcm.performance.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.service.TalentAnalyticsService;
import az.millers.hcm.performance.service.TalentAnalyticsService.AnalyticsReport;
import az.millers.hcm.security.SecurityRoles;

/**
 * HCM_15 M412 — talent analytics (PRD §15.9). HR-only reporting surface.
 */
@RestController
@RequestMapping("/api/talent/analytics")
public class TalentAnalyticsController {

    private final TalentAnalyticsService service;

    public TalentAnalyticsController(TalentAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public AnalyticsReport getAnalytics(@RequestParam UUID cycleId) {
        return service.report(cycleId);
    }
}
