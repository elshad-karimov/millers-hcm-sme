package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.ApplicationCreateRequest;
import az.millers.hcm.recruitment.api.dto.ApplicationEventResponse;
import az.millers.hcm.recruitment.api.dto.ApplicationResponse;
import az.millers.hcm.recruitment.api.dto.StageTransitionRequest;
import az.millers.hcm.recruitment.service.ApplicationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recruitment/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','DEPARTMENT_MANAGER')")
    public List<ApplicationResponse> list(
            @RequestParam(required = false) UUID vacancyId,
            @RequestParam(required = false) UUID candidateId) {
        if (vacancyId != null) {
            return service.forVacancy(vacancyId).stream()
                    .map(ApplicationResponse::from).toList();
        }
        if (candidateId != null) {
            return service.forCandidate(candidateId).stream()
                    .map(ApplicationResponse::from).toList();
        }
        return List.of();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApplicationResponse get(@PathVariable UUID id) {
        return ApplicationResponse.from(service.get(id));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public List<ApplicationEventResponse> history(@PathVariable UUID id) {
        return service.historyOf(id).stream().map(ApplicationEventResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public ApplicationResponse apply(@Valid @RequestBody ApplicationCreateRequest req) {
        return ApplicationResponse.from(service.apply(req.vacancyId(), req.candidateId()));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public ApplicationResponse transition(@PathVariable UUID id,
                                           @Valid @RequestBody StageTransitionRequest req) {
        return ApplicationResponse.from(service.transition(id, req));
    }
}
