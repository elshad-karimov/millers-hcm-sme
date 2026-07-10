package az.millers.hcm.policy.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.policy.domain.AcknowledgementCampaign;
import az.millers.hcm.policy.domain.CampaignAudience;
import az.millers.hcm.policy.domain.CampaignStatus;
import az.millers.hcm.policy.service.PolicyCampaignService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M490 — Policy re-acknowledgement campaign endpoints. HR-admin only.
 */
@RestController
@RequestMapping("/api/policies/campaigns")
public class PolicyCampaignController {

    private final PolicyCampaignService service;

    public PolicyCampaignController(PolicyCampaignService service) {
        this.service = service;
    }

    /**
     * List all campaigns.
     */
    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AcknowledgementCampaign> listCampaigns(
            @RequestParam(required = false) CampaignStatus status) {
        if (status != null) {
            return service.listByStatus(status);
        }
        return service.listAll();
    }

    /**
     * Get a single campaign.
     */
    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public AcknowledgementCampaign getCampaign(@PathVariable UUID id) {
        return service.get(id);
    }

    /**
     * Create a new campaign in DRAFT.
     */
    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AcknowledgementCampaign createCampaign(
            @RequestBody CreateCampaignRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return service.create(
            req.policyId,
            req.policyVersion,
            req.name,
            req.audience,
            req.audienceRef,
            req.dueDate,
            username
        );
    }

    /**
     * Launch a DRAFT campaign → notify audience.
     */
    @PostMapping("/{id}/launch")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void launchCampaign(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        service.launch(id, username);
    }

    /**
     * Close an ACTIVE campaign.
     */
    @PostMapping("/{id}/close")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void closeCampaign(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        service.close(id, username);
    }

    /**
     * Get campaign progress (acked vs total, per-department).
     */
    @GetMapping("/{id}/progress")
    @PreAuthorize(SecurityRoles.READ_HR)
    public PolicyCampaignService.CampaignProgress getProgress(@PathVariable UUID id) {
        return service.getProgress(id);
    }

    // ── DTOs ───────────────────────────────────────────────────────────────────

    public record CreateCampaignRequest(
        UUID policyId,
        int policyVersion,
        String name,
        CampaignAudience audience,
        UUID audienceRef,
        LocalDate dueDate
    ) {}
}
