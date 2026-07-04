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

import az.millers.hcm.recruitment.api.dto.PreHireCheckDtos.CheckRequest;
import az.millers.hcm.recruitment.api.dto.PreHireCheckDtos.CheckResponse;
import az.millers.hcm.recruitment.api.dto.PreHireCheckDtos.CheckUpdate;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.recruitment.service.PreHireCheckService;

/** M286 — Recruitment PRD §25-§27 pre-hire check REST. */
@RestController
@RequestMapping("/api/recruitment")
public class PreHireCheckController {

    private final PreHireCheckService service;

    public PreHireCheckController(PreHireCheckService service) {
        this.service = service;
    }

    @GetMapping("/applications/{applicationId}/checks")
    @PreAuthorize(SecurityRoles.READ_INTERVIEWS)
    public List<CheckResponse> list(@PathVariable UUID applicationId) {
        return service.listForApplication(applicationId);
    }

    @PostMapping("/applications/{applicationId}/checks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public CheckResponse create(@PathVariable UUID applicationId,
                                 @Valid @RequestBody CheckRequest req) {
        return service.create(applicationId, req);
    }

    @PutMapping("/checks/{id}")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public CheckResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody CheckUpdate req) {
        return service.update(id, req);
    }
}
