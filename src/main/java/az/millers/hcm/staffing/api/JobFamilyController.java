package az.millers.hcm.staffing.api;

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

import az.millers.hcm.staffing.api.dto.JobFamilyRequest;
import az.millers.hcm.staffing.api.dto.JobFamilyResponse;
import az.millers.hcm.staffing.service.JobFamilyService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/job-families")
public class JobFamilyController {

    private final JobFamilyService service;

    public JobFamilyController(JobFamilyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','FINANCE_USER')")
    public List<JobFamilyResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.list(activeOnly).stream().map(JobFamilyResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','FINANCE_USER')")
    public JobFamilyResponse get(@PathVariable UUID id) {
        return JobFamilyResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public JobFamilyResponse create(@Valid @RequestBody JobFamilyRequest req) {
        return JobFamilyResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public JobFamilyResponse update(@PathVariable UUID id, @Valid @RequestBody JobFamilyRequest req) {
        return JobFamilyResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
