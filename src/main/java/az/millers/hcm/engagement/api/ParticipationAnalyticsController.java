package az.millers.hcm.engagement.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.engagement.service.ParticipationAnalyticsService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M480 — Participation analytics with anonymity guard (HR_ADMIN/HR_SPECIALIST).
 */
@RestController
@RequestMapping("/api/engagement/campaigns")
public class ParticipationAnalyticsController {

    private final ParticipationAnalyticsService service;

    public ParticipationAnalyticsController(ParticipationAnalyticsService service) {
        this.service = service;
    }

    /**
     * Campaign participation analytics: overall rate + department breakdown.
     * ANONYMITY: any group with responded < threshold returns suppressed=true.
     */
    @GetMapping("/{campaignId}/participation")
    @PreAuthorize(SecurityRoles.READ_HR)
    public Map<String, Object> participation(@PathVariable UUID campaignId) {
        return service.participation(campaignId);
    }

    /**
     * Simple sentiment on open-text answers (aggregate counts ONLY).
     */
    @GetMapping("/{campaignId}/sentiment")
    @PreAuthorize(SecurityRoles.READ_HR)
    public Map<String, Object> sentiment(@PathVariable UUID campaignId) {
        return service.sentiment(campaignId);
    }
}
