package az.millers.hcm.engagement.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.engagement.api.dto.SurveyDtos.CampaignResponse;
import az.millers.hcm.engagement.api.dto.SurveyDtos.ResponseResponse;
import az.millers.hcm.engagement.api.dto.SurveyDtos.SubmitRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.TemplateResponse;
import az.millers.hcm.engagement.service.SurveyService;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import jakarta.validation.Valid;

/**
 * Employee self-service surface (M116). Any authenticated user can list
 * active campaigns and submit a response.
 */
@RestController
@RequestMapping("/api/me/surveys")
@PreAuthorize("isAuthenticated()")
public class MySurveysController {

    private final SurveyService service;
    private final EmployeeContextService context;

    public MySurveysController(SurveyService service, EmployeeContextService context) {
        this.service = service;
        this.context = context;
    }

    /** Open campaigns that respect today's date window. */
    @GetMapping
    public List<CampaignResponse> openToday() {
        return service.openCampaignsToday();
    }

    @GetMapping("/templates/{id}")
    public TemplateResponse template(@PathVariable UUID id) {
        return service.getTemplate(id);
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResponse submit(@Valid @RequestBody SubmitRequest req) {
        UUID empId = context.currentEmployee().getId();
        return service.submit(req, empId);
    }
}
