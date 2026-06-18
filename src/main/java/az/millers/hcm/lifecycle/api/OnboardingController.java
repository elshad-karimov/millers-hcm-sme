package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.AcknowledgeRequest;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.AcknowledgementResponse;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.BuddyAssignRequest;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.BuddyResponse;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.MeetingResponse;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingJourney;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingOverview;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.ScheduleMeetingRequest;
import az.millers.hcm.lifecycle.api.dto.ResourceRequestDtos.FulfillRequest;
import az.millers.hcm.lifecycle.api.dto.ResourceRequestDtos.ProvisioningQueueSummary;
import az.millers.hcm.lifecycle.api.dto.ResourceRequestDtos.ResourceRequestResponse;
import az.millers.hcm.lifecycle.api.dto.ResourceRequestDtos.UpdateStatusRequest;
import az.millers.hcm.lifecycle.service.OnboardingAcknowledgementService;
import az.millers.hcm.lifecycle.service.OnboardingBuddyService;
import az.millers.hcm.lifecycle.service.OnboardingMeetingService;
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
    private final OnboardingAcknowledgementService acknowledgements;
    private final OnboardingBuddyService buddies;
    private final OnboardingMeetingService meetings;

    public OnboardingController(OnboardingService service,
                               OnboardingResourceRequestService requests,
                               OnboardingAcknowledgementService acknowledgements,
                               OnboardingBuddyService buddies,
                               OnboardingMeetingService meetings) {
        this.service = service;
        this.requests = requests;
        this.acknowledgements = acknowledgements;
        this.buddies = buddies;
        this.meetings = meetings;
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

    /** M310 — Summary stat cards for the provisioning queue dashboard. */
    @GetMapping("/requests/summary")
    @PreAuthorize(READ)
    public ProvisioningQueueSummary requestsSummary() {
        return requests.queueSummary();
    }

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
        return requests.updateStatus(id, req.status(), req.details(), req.provisioningKind());
    }

    @PostMapping("/requests/{id}/fulfill")
    @PreAuthorize(WRITE)
    public ResourceRequestResponse fulfillRequest(@PathVariable UUID id,
                                                  @RequestBody FulfillRequest req) {
        return requests.fulfill(id, req);
    }

    // ── M304 — policy / contract / document acknowledgements ─────────────────

    @PostMapping("/acknowledgements")
    @PreAuthorize(WRITE)
    public AcknowledgementResponse acknowledge(@RequestBody AcknowledgeRequest req) {
        return acknowledgements.acknowledge(req.taskStatusId(), req.statement(), req.reference());
    }

    @GetMapping("/acknowledgements/employees/{employeeId}")
    @PreAuthorize(READ)
    public List<AcknowledgementResponse> acknowledgementsForEmployee(@PathVariable UUID employeeId) {
        return acknowledgements.listForEmployee(employeeId);
    }

    // ── M305 — buddy / mentor assignment ─────────────────────────────────────

    @PostMapping("/buddies")
    @PreAuthorize(WRITE)
    public BuddyResponse assignBuddy(@RequestBody BuddyAssignRequest req) {
        return buddies.assign(req.employeeId(), req.buddyEmployeeId(), req.role(),
                req.taskStatusId(), req.notes());
    }

    @PostMapping("/buddies/{id}/end")
    @PreAuthorize(WRITE)
    public BuddyResponse endBuddy(@PathVariable UUID id,
                                  @RequestParam(required = false) String reason) {
        return buddies.end(id, reason);
    }

    @GetMapping("/buddies/employees/{employeeId}")
    @PreAuthorize(READ)
    public List<BuddyResponse> buddiesForEmployee(@PathVariable UUID employeeId) {
        return buddies.forEmployee(employeeId);
    }

    @GetMapping("/buddies/mentees/{buddyEmployeeId}")
    @PreAuthorize(READ)
    public List<BuddyResponse> mentees(@PathVariable UUID buddyEmployeeId) {
        return buddies.mentees(buddyEmployeeId);
    }

    // ── M306 — first-day meeting scheduling / calendar invites ───────────────

    @PostMapping("/meetings")
    @PreAuthorize(WRITE)
    public MeetingResponse scheduleMeeting(@RequestBody ScheduleMeetingRequest req) {
        return meetings.schedule(req);
    }

    @PutMapping("/meetings/{id}")
    @PreAuthorize(WRITE)
    public MeetingResponse rescheduleMeeting(@PathVariable UUID id,
                                              @RequestBody ScheduleMeetingRequest req) {
        return meetings.reschedule(id, req);
    }

    @PostMapping("/meetings/{id}/cancel")
    @PreAuthorize(WRITE)
    public MeetingResponse cancelMeeting(@PathVariable UUID id,
                                          @RequestParam(required = false) String reason) {
        return meetings.cancel(id, reason);
    }

    @GetMapping("/meetings/employees/{employeeId}")
    @PreAuthorize(READ)
    public List<MeetingResponse> meetingsForEmployee(@PathVariable UUID employeeId) {
        return meetings.forEmployee(employeeId);
    }
}
