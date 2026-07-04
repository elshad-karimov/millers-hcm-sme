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

import az.millers.hcm.recruitment.api.dto.AssessmentDtos.AssessmentRequest;
import az.millers.hcm.recruitment.api.dto.AssessmentDtos.AssessmentResponse;
import az.millers.hcm.recruitment.api.dto.AssessmentDtos.AssessmentUpdate;
import az.millers.hcm.recruitment.service.AssessmentService;
import az.millers.hcm.security.SecurityRoles;

/** M287 — Recruitment PRD §22 assessment REST. */
@RestController
@RequestMapping("/api/recruitment")
public class AssessmentController {

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    @GetMapping("/applications/{applicationId}/assessments")
    @PreAuthorize(SecurityRoles.READ_INTERVIEWS)
    public List<AssessmentResponse> list(@PathVariable UUID applicationId) {
        return service.listForApplication(applicationId);
    }

    @PostMapping("/applications/{applicationId}/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public AssessmentResponse create(@PathVariable UUID applicationId,
                                      @Valid @RequestBody AssessmentRequest req) {
        return service.create(applicationId, req);
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public AssessmentResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody AssessmentUpdate req) {
        return service.update(id, req);
    }
}
