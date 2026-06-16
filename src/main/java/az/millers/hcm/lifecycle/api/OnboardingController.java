package az.millers.hcm.lifecycle.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingJourney;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingOverview;
import az.millers.hcm.lifecycle.service.OnboardingService;
import az.millers.hcm.security.SecurityRoles;

/**
 * Onboarding journey hub + HR console (M300 — Onboarding Phase A.3).
 * Read-only projections over the M105 checklist engine, scoped to the
 * ONBOARDING flow and enriched with employee + operational context.
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private static final String READ = SecurityRoles.READ_HR;

    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    /** HR console: active onboardings + stats + pending-by-type / by-department. */
    @GetMapping("/overview")
    @PreAuthorize(READ)
    public OnboardingOverview overview() {
        return service.overview();
    }

    /** A new hire's onboarding journey (their active onboarding assignment + context). */
    @GetMapping("/journey/{employeeId}")
    @PreAuthorize(READ)
    public OnboardingJourney journey(@PathVariable UUID employeeId) {
        return service.journey(employeeId);
    }
}
