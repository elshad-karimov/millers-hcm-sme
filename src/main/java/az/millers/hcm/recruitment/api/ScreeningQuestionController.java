package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.ScreeningDtos.AnswerResponse;
import az.millers.hcm.recruitment.api.dto.ScreeningDtos.QuestionRequest;
import az.millers.hcm.recruitment.api.dto.ScreeningDtos.QuestionResponse;
import az.millers.hcm.recruitment.service.ScreeningQuestionService;
import az.millers.hcm.security.SecurityRoles;

/** M289 — Recruitment PRD §15 knockout / screening question REST (HR side). */
@RestController
@RequestMapping("/api/recruitment")
public class ScreeningQuestionController {

    private final ScreeningQuestionService service;

    public ScreeningQuestionController(ScreeningQuestionService service) {
        this.service = service;
    }

    @GetMapping("/postings/{postingId}/screening-questions")
    @PreAuthorize(SecurityRoles.READ_INTERVIEWS)
    public List<QuestionResponse> list(@PathVariable UUID postingId) {
        return service.listForPosting(postingId);
    }

    @PostMapping("/postings/{postingId}/screening-questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public QuestionResponse create(@PathVariable UUID postingId,
                                    @Valid @RequestBody QuestionRequest req) {
        return service.create(postingId, req);
    }

    @PutMapping("/screening-questions/{id}")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public QuestionResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody QuestionRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/screening-questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** The candidate's stored answers + knockout outcome (recruiter view). */
    @GetMapping("/applications/{applicationId}/screening-answers")
    @PreAuthorize(SecurityRoles.READ_INTERVIEWS)
    public List<AnswerResponse> answers(@PathVariable UUID applicationId) {
        return service.answersForApplication(applicationId);
    }
}
