package az.millers.hcm.letters.api;

import az.millers.hcm.security.SecurityRoles;

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

import az.millers.hcm.letters.api.dto.LetterTemplateRequest;
import az.millers.hcm.letters.api.dto.LetterTemplateResponse;
import az.millers.hcm.letters.service.LetterTemplateService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/letter-templates")
public class LetterTemplateController {

    private final LetterTemplateService service;

    public LetterTemplateController(LetterTemplateService service) {
        this.service = service;
    }

    /** Read open to any authenticated user — needed by the self-service form. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<LetterTemplateResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.list(activeOnly).stream().map(LetterTemplateResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public LetterTemplateResponse get(@PathVariable UUID id) {
        return LetterTemplateResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LetterTemplateResponse create(@Valid @RequestBody LetterTemplateRequest req) {
        return LetterTemplateResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LetterTemplateResponse update(@PathVariable UUID id,
                                          @Valid @RequestBody LetterTemplateRequest req) {
        return LetterTemplateResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }
}
