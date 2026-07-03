package az.millers.hcm.performance.api;

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

import az.millers.hcm.performance.api.dto.CompetencyAssessmentDtos.AddCompetencyRequest;
import az.millers.hcm.performance.api.dto.CompetencyAssessmentDtos.AssessmentResponse;
import az.millers.hcm.performance.api.dto.CompetencyAssessmentDtos.RateRequest;
import az.millers.hcm.performance.service.PerfCompetencyAssessmentService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * Per-review competency assessment API (HCM_12 M393). All operations are
 * hierarchy-guarded in the service (AccessScopeService on the review's employee).
 */
@RestController
@RequestMapping("/api/performance/reviews/{reviewId}/competencies")
public class PerfCompetencyAssessmentController {

    private final PerfCompetencyAssessmentService service;

    public PerfCompetencyAssessmentController(PerfCompetencyAssessmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AssessmentResponse> list(@PathVariable UUID reviewId) {
        return service.list(reviewId);
    }

    /** Seed from the employee's position requirements (idempotent). */
    @PostMapping("/init")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public List<AssessmentResponse> init(@PathVariable UUID reviewId) {
        return service.init(reviewId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public AssessmentResponse add(@PathVariable UUID reviewId,
                                  @Valid @RequestBody AddCompetencyRequest req) {
        return service.add(reviewId, req);
    }

    @PostMapping("/{assessmentId}/rate")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public AssessmentResponse rate(@PathVariable UUID reviewId,
                                   @PathVariable UUID assessmentId,
                                   @Valid @RequestBody RateRequest req) {
        return service.rate(assessmentId, req);
    }
}
