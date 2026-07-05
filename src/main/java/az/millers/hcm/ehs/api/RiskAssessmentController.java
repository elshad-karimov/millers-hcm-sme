package az.millers.hcm.ehs.api;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.ehs.api.dto.RiskAssessmentDtos.*;
import az.millers.hcm.ehs.domain.RiskAssessment;
import az.millers.hcm.ehs.domain.RiskAssessmentStatus;
import az.millers.hcm.ehs.service.RiskAssessmentService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M450 — EHS risk assessment management.
 */
@RestController
@RequestMapping("/api/ehs/risk-assessments")
public class RiskAssessmentController {

    private final RiskAssessmentService service;

    public RiskAssessmentController(RiskAssessmentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public RiskAssessmentResponse create(@RequestBody CreateRiskAssessmentRequest req) {
        RiskAssessment assessment = service.create(
                req.workLocationId(),
                req.orgUnitId(),
                req.jobTask(),
                req.hazard(),
                req.likelihood(),
                req.impact(),
                req.controlMeasures(),
                req.responsibleUsername(),
                req.reviewDate()
        );

        return RiskAssessmentResponse.from(assessment);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public RiskAssessmentResponse update(@PathVariable UUID id,
                                          @RequestBody UpdateRiskAssessmentRequest req) {
        RiskAssessment assessment = service.update(
                id,
                req.jobTask(),
                req.hazard(),
                req.likelihood(),
                req.impact(),
                req.controlMeasures(),
                req.responsibleUsername(),
                req.reviewDate()
        );

        return RiskAssessmentResponse.from(assessment);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public RiskAssessmentResponse approve(@PathVariable UUID id) {
        service.approve(id);
        RiskAssessment assessment = service.get(id);
        return RiskAssessmentResponse.from(assessment);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public RiskAssessmentResponse get(@PathVariable UUID id) {
        RiskAssessment assessment = service.get(id);
        return RiskAssessmentResponse.from(assessment);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<RiskAssessmentResponse> list(
            @RequestParam(required = false) RiskAssessmentStatus status,
            @RequestParam(required = false) Integer minRiskScore) {

        return service.list(status, minRiskScore).stream()
                .map(RiskAssessmentResponse::from)
                .collect(Collectors.toList());
    }
}
