package az.millers.hcm.performance.api;

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

import az.millers.hcm.performance.api.dto.Feedback360Dtos.NominateRequest;
import az.millers.hcm.performance.api.dto.Feedback360Dtos.NominationResponse;
import az.millers.hcm.performance.api.dto.Feedback360Dtos.NominationSummary;
import az.millers.hcm.performance.api.dto.Feedback360Dtos.QuestionnaireRequest;
import az.millers.hcm.performance.api.dto.Feedback360Dtos.QuestionnaireResponse;
import az.millers.hcm.performance.api.dto.FeedbackRequest;
import az.millers.hcm.performance.service.Feedback360Service;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * 360° nomination + questionnaire API (HCM_12 M395). Nomination reads/writes are
 * hierarchy-guarded in the service; questionnaire admin is HR-only.
 */
@RestController
@RequestMapping("/api/performance/feedback360")
public class Feedback360Controller {

    private final Feedback360Service service;

    public Feedback360Controller(Feedback360Service service) {
        this.service = service;
    }

    // ── Questionnaires (§13.3) ───────────────────────────────────────────────

    @GetMapping("/questionnaires")
    @PreAuthorize("isAuthenticated()")
    public List<QuestionnaireResponse> questionnaires(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listQuestionnaires(activeOnly);
    }

    @PostMapping("/questionnaires")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public QuestionnaireResponse createQuestionnaire(@Valid @RequestBody QuestionnaireRequest req) {
        return service.createQuestionnaire(req);
    }

    @PutMapping("/questionnaires/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public QuestionnaireResponse updateQuestionnaire(@PathVariable UUID id,
                                                     @Valid @RequestBody QuestionnaireRequest req) {
        return service.updateQuestionnaire(id, req);
    }

    // ── Nominations (§13.2) ──────────────────────────────────────────────────

    @GetMapping("/requests")
    @PreAuthorize("isAuthenticated()")
    public List<NominationResponse> requests(@RequestParam(required = false) UUID cycleId,
                                             @RequestParam(required = false) UUID subjectEmployeeId,
                                             @RequestParam(required = false) UUID reviewerEmployeeId) {
        return service.listNominations(cycleId, subjectEmployeeId, reviewerEmployeeId);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public NominationResponse nominate(@Valid @RequestBody NominateRequest req) {
        return service.nominate(req);
    }

    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public NominationResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/requests/{id}/decline")
    @PreAuthorize("isAuthenticated()")
    public NominationResponse decline(@PathVariable UUID id,
                                      @RequestParam(required = false) String reason) {
        return service.decline(id, reason);
    }

    @PostMapping("/requests/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public NominationResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    /** Reviewer submits the response — creates the feedback row + links it. */
    @PostMapping("/requests/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public NominationResponse complete(@PathVariable UUID id,
                                       @RequestBody FeedbackRequest content) {
        return service.complete(id, content);
    }

    /** §13.4 completeness summary for a subject in a cycle. */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public NominationSummary summary(@RequestParam UUID cycleId,
                                     @RequestParam UUID subjectEmployeeId) {
        return service.summary(cycleId, subjectEmployeeId);
    }
}
