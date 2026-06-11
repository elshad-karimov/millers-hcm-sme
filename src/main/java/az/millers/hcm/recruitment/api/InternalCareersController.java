package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.service.InternalCareersService;
import az.millers.hcm.recruitment.service.InternalCareersService.InternalApplyResult;
import az.millers.hcm.recruitment.service.InternalCareersService.InternalJob;

/**
 * M281 — Recruitment PRD §10: internal career portal REST. Any
 * authenticated employee can browse and apply; the employee identity
 * comes from the token via EmployeeContextService.
 */
@RestController
@RequestMapping("/api/self/internal-jobs")
public class InternalCareersController {

    private final InternalCareersService service;

    public InternalCareersController(InternalCareersService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<InternalJob> list() {
        return service.listLive();
    }

    @PostMapping("/{postingId}/apply")
    @PreAuthorize("isAuthenticated()")
    public InternalApplyResult apply(@PathVariable UUID postingId) {
        return service.apply(postingId);
    }
}
