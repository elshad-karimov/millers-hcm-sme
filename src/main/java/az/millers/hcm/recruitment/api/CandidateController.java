package az.millers.hcm.recruitment.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.recruitment.api.dto.CandidateRequest;
import az.millers.hcm.recruitment.api.dto.CandidateResponse;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.service.CandidateService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recruitment/candidates")
public class CandidateController {

    private final CandidateService service;

    public CandidateController(CandidateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public PageResponse<CandidateResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Candidate> rows = service.list(search, pageable);
        return PageResponse.of(rows, CandidateResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CandidateResponse get(@PathVariable UUID id) {
        return CandidateResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public CandidateResponse create(@Valid @RequestBody CandidateRequest req) {
        return CandidateResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public CandidateResponse update(@PathVariable UUID id, @Valid @RequestBody CandidateRequest req) {
        return CandidateResponse.from(service.update(id, req));
    }
}
