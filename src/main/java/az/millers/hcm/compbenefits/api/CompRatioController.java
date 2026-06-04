package az.millers.hcm.compbenefits.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.api.dto.CompRatioDtos.CompRatioReport;
import az.millers.hcm.compbenefits.service.CompRatioService;
import az.millers.hcm.security.SecurityRoles;

/**
 * Comp-ratio / salary-planning analytics endpoint (M102).
 * HR_ADMIN-only: salary data is among the most sensitive PII in the system.
 */
@RestController
@RequestMapping("/api/compbenefits/comp-ratio")
public class CompRatioController {

    private static final String READ = SecurityRoles.WRITE_HR_ADMIN_ONLY;

    private final CompRatioService service;

    public CompRatioController(CompRatioService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    public CompRatioReport report() {
        return service.report();
    }
}
