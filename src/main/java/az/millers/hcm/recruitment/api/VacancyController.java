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
import az.millers.hcm.recruitment.api.dto.VacancyRequest;
import az.millers.hcm.recruitment.api.dto.VacancyResponse;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.domain.VacancyStatus;
import az.millers.hcm.recruitment.service.VacancyService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recruitment/vacancies")
public class VacancyController {

    private final VacancyService service;

    public VacancyController(VacancyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_INTERVIEWS)
    public PageResponse<VacancyResponse> list(
            @RequestParam(required = false) VacancyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Vacancy> rows = service.list(status, pageable);
        return PageResponse.of(rows, VacancyResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public VacancyResponse get(@PathVariable UUID id) {
        return VacancyResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public VacancyResponse create(@Valid @RequestBody VacancyRequest req) {
        return VacancyResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public VacancyResponse update(@PathVariable UUID id, @Valid @RequestBody VacancyRequest req) {
        return VacancyResponse.from(service.update(id, req));
    }

    @PostMapping("/{id}/status/{status}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public VacancyResponse changeStatus(@PathVariable UUID id,
                                         @PathVariable VacancyStatus status,
                                         @RequestParam(required = false) String reason) {
        return VacancyResponse.from(service.changeStatus(id, status, reason));
    }
}
