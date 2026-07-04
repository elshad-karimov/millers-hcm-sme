package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.InterviewDtos.KitRequest;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.KitResponse;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.QuestionRequest;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.QuestionResponse;
import az.millers.hcm.recruitment.service.InterviewKitService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recruitment/interview-kits")
public class InterviewKitController {

    /** Centralised role sets — see {@link az.millers.hcm.security.SecurityRoles}. */
    private static final String READ_ROLES = az.millers.hcm.security.SecurityRoles.READ_RECRUITMENT;
    private static final String WRITE_ROLES = az.millers.hcm.security.SecurityRoles.WRITE_RECRUITMENT;

    private final InterviewKitService service;

    public InterviewKitController(InterviewKitService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<KitResponse> list(@RequestParam(required = false) UUID jobFamilyId,
                                   @RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.list(jobFamilyId, activeOnly).stream().map(KitResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public KitResponse get(@PathVariable UUID id) {
        return KitResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public KitResponse create(@Valid @RequestBody KitRequest req) {
        return KitResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public KitResponse update(@PathVariable UUID id, @Valid @RequestBody KitRequest req) {
        return KitResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    // ── Nested questions ────────────────────────────────────────────────────

    @GetMapping("/{kitId}/questions")
    @PreAuthorize(READ_ROLES)
    public List<QuestionResponse> listQuestions(@PathVariable UUID kitId,
                                                  @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listQuestions(kitId, activeOnly).stream()
                .map(QuestionResponse::from).toList();
    }

    @PostMapping("/{kitId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public QuestionResponse addQuestion(@PathVariable UUID kitId,
                                          @Valid @RequestBody QuestionRequest req) {
        return QuestionResponse.from(service.addQuestion(kitId, req));
    }

    @PutMapping("/{kitId}/questions/{questionId}")
    @PreAuthorize(WRITE_ROLES)
    public QuestionResponse updateQuestion(@PathVariable UUID kitId,
                                             @PathVariable UUID questionId,
                                             @Valid @RequestBody QuestionRequest req) {
        return QuestionResponse.from(service.updateQuestion(questionId, req));
    }

    @DeleteMapping("/{kitId}/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteQuestion(@PathVariable UUID kitId, @PathVariable UUID questionId) {
        service.deleteQuestion(questionId);
    }
}
