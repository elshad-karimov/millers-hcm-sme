package az.millers.hcm.performance.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.service.SuccessionChartService;
import az.millers.hcm.performance.service.SuccessionChartService.ReplacementChartResponse;
import az.millers.hcm.security.SecurityRoles;

/**
 * HCM_16 M415 — replacement chart & dev-plan attachment (PRD §16.5).
 * HR-only: shows critical positions with incumbents + successors + coverage.
 */
@RestController
@RequestMapping("/api/succession")
public class SuccessionChartController {

    private static final String HR = SecurityRoles.READ_HR;

    private final SuccessionChartService service;

    public SuccessionChartController(SuccessionChartService service) {
        this.service = service;
    }

    @GetMapping("/replacement-chart")
    @PreAuthorize(HR)
    public ReplacementChartResponse replacementChart() {
        return service.replacementChart();
    }

    @PostMapping("/nominations/{nominationId}/dev-plans")
    @PreAuthorize(HR)
    public void attachDevPlan(@PathVariable UUID nominationId,
                               @RequestBody DevPlanRequest req) {
        service.attachDevPlan(nominationId, req.devPlanId);
    }

    @DeleteMapping("/nominations/{nominationId}/dev-plans/{devPlanId}")
    @PreAuthorize(HR)
    public void detachDevPlan(@PathVariable UUID nominationId,
                               @PathVariable UUID devPlanId) {
        service.detachDevPlan(nominationId, devPlanId);
    }

    public record DevPlanRequest(UUID devPlanId) {}
}
