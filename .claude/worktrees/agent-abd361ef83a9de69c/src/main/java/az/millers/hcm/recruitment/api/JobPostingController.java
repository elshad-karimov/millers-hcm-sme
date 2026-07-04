package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.JobPostingDtos.PostingRequest;
import az.millers.hcm.recruitment.api.dto.JobPostingDtos.PostingResponse;
import az.millers.hcm.recruitment.service.JobPostingService;

/** M278 — Recruitment PRD §8 job posting REST. */
@RestController
@RequestMapping("/api/recruitment")
public class JobPostingController {

    private final JobPostingService service;

    public JobPostingController(JobPostingService service) {
        this.service = service;
    }

    @GetMapping("/vacancies/{vacancyId}/postings")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public List<PostingResponse> listForVacancy(@PathVariable UUID vacancyId) {
        return service.listForVacancy(vacancyId).stream()
                .map(PostingResponse::from)
                .toList();
    }

    @PostMapping("/vacancies/{vacancyId}/postings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public PostingResponse create(@PathVariable UUID vacancyId,
                                   @Valid @RequestBody PostingRequest req) {
        return PostingResponse.from(service.create(vacancyId, req));
    }

    @PutMapping("/postings/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public PostingResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody PostingRequest req) {
        return PostingResponse.from(service.update(id, req));
    }

    @PostMapping("/postings/{id}/publish")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public PostingResponse publish(@PathVariable UUID id) {
        return PostingResponse.from(service.publish(id));
    }

    @PostMapping("/postings/{id}/pause")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public PostingResponse pause(@PathVariable UUID id) {
        return PostingResponse.from(service.pause(id));
    }

    @PostMapping("/postings/{id}/close")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public PostingResponse close(@PathVariable UUID id) {
        return PostingResponse.from(service.close(id));
    }
}
