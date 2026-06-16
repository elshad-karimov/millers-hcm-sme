package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingJourney;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingOverview;
import az.millers.hcm.lifecycle.api.dto.ResourceRequestDtos.FulfillRequest;
import az.millers.hcm.lifecycle.api.dto.ResourceRequestDtos.ResourceRequestResponse;
import az.millers.hcm.lifecycle.api.dto.ResourceRequestDtos.UpdateStatusRequest;
import az.millers.hcm.lifecycle.service.OnboardingResourceRequestService;
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
    private static final String WRITE = SecurityRoles.WRITE_HR;

    private final OnboardingService service;
    private final OnboardingResourceRequestService requests;

    public OnboardingController(OnboardingService service,
                               OnboardingResourceRequestService requests) {
        this.service = service;
        this.requests = requests;
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

    // ── M301 — equipment / workspace provisioning requests ───────────────────

    /** Open IT/Facilities provisioning queue (REQUESTED + IN_PROGRESS). */
    @GetMapping("/requests")
    @PreAuthorize(READ)
    public List<ResourceRequestResponse> openRequests() {
        return requests.listOpen();
    }

    /** A hire's provisioning requests (for the journey drawer). */
    @GetMapping("/requests/employees/{employeeId}")
    @PreAuthorize(READ)
    public List<ResourceRequestResponse> requestsForEmployee(@PathVariable UUID employeeId) {
        return requests.listForEmployee(employeeId);
    }

    @PostMapping("/requests/{id}/status")
    @PreAuthorize(WRITE)
    public ResourceRequestResponse updateRequestStatus(@PathVariable UUID id,
                                                       @RequestBody UpdateStatusRequest req) {
        return requests.updateStatus(id, req.status(), req.details());
    }

    @PostMapping("/requests/{id}/fulfill")
    @PreAuthorize(WRITE)
    public ResourceRequestResponse fulfillRequest(@PathVariable UUID id,
                                                  @RequestBody FulfillRequest req) {
        return requests.fulfill(id, req);
    }
}
