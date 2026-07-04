package az.millers.hcm.engagement.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.engagement.api.dto.SurveyDtos.CampaignRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.CampaignResponse;
import az.millers.hcm.engagement.api.dto.SurveyDtos.CampaignResults;
import az.millers.hcm.engagement.api.dto.SurveyDtos.ResponseResponse;
import az.millers.hcm.engagement.api.dto.SurveyDtos.TemplateRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.TemplateResponse;
import az.millers.hcm.engagement.domain.CampaignStatus;
import az.millers.hcm.engagement.service.SurveyService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * HR admin surface for the engagement module (M116). Templates +
 * campaigns + results. Submission lives on the employee controller.
 *
 * <ul>
 *   <li>Template CRUD — HR_ADMIN_ONLY (changes ripple to every campaign).</li>
 *   <li>Campaigns + reads — HR_PLUS_MANAGERS so leads can review their
 *       team's engagement scores.</li>
 *   <li>Results — same as reads. Anonymous responses don't carry an
 *       employee_id so individual identity can't leak through this surface.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/engagement")
public class SurveyAdminController {

    private final SurveyService service;

    public SurveyAdminController(SurveyService service) {
        this.service = service;
    }

    // ── Templates ───────────────────────────────────────────────────────

    @GetMapping("/templates")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<TemplateResponse> listTemplates(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listTemplates(activeOnly);
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public TemplateResponse getTemplate(@PathVariable UUID id) {
        return service.getTemplate(id);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TemplateResponse createTemplate(@Valid @RequestBody TemplateRequest req) {
        return service.createTemplate(req);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TemplateResponse updateTemplate(@PathVariable UUID id, @Valid @RequestBody TemplateRequest req) {
        return service.updateTemplate(id, req);
    }

    // ── Campaigns ───────────────────────────────────────────────────────

    @GetMapping("/campaigns")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<CampaignResponse> listCampaigns(@RequestParam(required = false) CampaignStatus status) {
        return service.listCampaigns(status);
    }

    @GetMapping("/campaigns/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public CampaignResponse getCampaign(@PathVariable UUID id) {
        return service.getCampaign(id);
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public CampaignResponse createCampaign(@Valid @RequestBody CampaignRequest req) {
        return service.createCampaign(req);
    }

    public record StatusChangeRequest(CampaignStatus status) {}

    @PostMapping("/campaigns/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public CampaignResponse changeStatus(@PathVariable UUID id,
                                          @RequestBody StatusChangeRequest req) {
        return service.changeStatus(id, req.status());
    }

    // ── Results ─────────────────────────────────────────────────────────

    @GetMapping("/campaigns/{id}/results")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public CampaignResults results(@PathVariable UUID id) {
        return service.results(id);
    }

    @GetMapping("/campaigns/{id}/responses")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<ResponseResponse> responses(@PathVariable UUID id) {
        return service.responsesFor(id);
    }
}
