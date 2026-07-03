package az.millers.hcm.performance.api;

import java.util.List;
import java.util.UUID;

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

import az.millers.hcm.performance.api.dto.PerfTemplateDtos.TemplateRequest;
import az.millers.hcm.performance.api.dto.PerfTemplateDtos.TemplateResponse;
import az.millers.hcm.performance.service.PerfTemplateService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/** Review template API (HCM_12 M389). Reads for HR+managers; writes HR-admin only. */
@RestController
@RequestMapping("/api/performance/templates")
public class PerfTemplateController {

    private final PerfTemplateService service;

    public PerfTemplateController(PerfTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<TemplateResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public TemplateResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TemplateResponse create(@Valid @RequestBody TemplateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TemplateResponse update(@PathVariable UUID id, @Valid @RequestBody TemplateRequest req) {
        return service.update(id, req);
    }
}
