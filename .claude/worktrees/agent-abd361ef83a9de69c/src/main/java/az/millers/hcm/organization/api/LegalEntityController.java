package az.millers.hcm.organization.api;

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

import az.millers.hcm.organization.api.dto.LegalEntityDtos.LegalEntityRequest;
import az.millers.hcm.organization.api.dto.LegalEntityDtos.LegalEntityResponse;
import az.millers.hcm.organization.service.LegalEntityService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * M140 — admin surface for the legal-entity master. Read on
 * {@link SecurityRoles#READ_HR_PLUS_MANAGERS} so line managers can see
 * the catalogue; mutations restricted to {@link SecurityRoles#WRITE_HR_ADMIN_ONLY}.
 */
@RestController
@RequestMapping("/api/legal-entities")
public class LegalEntityController {

    private final LegalEntityService service;

    public LegalEntityController(LegalEntityService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<LegalEntityResponse> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        boolean bank = service.canSeeBankPlain();
        return service.list(activeOnly).stream()
                .map(e -> LegalEntityResponse.from(e, bank))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public LegalEntityResponse get(@PathVariable UUID id) {
        return LegalEntityResponse.from(service.get(id), service.canSeeBankPlain());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LegalEntityResponse create(@Valid @RequestBody LegalEntityRequest req) {
        return LegalEntityResponse.from(service.create(req), true);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LegalEntityResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody LegalEntityRequest req) {
        return LegalEntityResponse.from(service.update(id, req), true);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LegalEntityResponse activate(@PathVariable UUID id) {
        return LegalEntityResponse.from(service.setActive(id, true), true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LegalEntityResponse deactivate(@PathVariable UUID id) {
        return LegalEntityResponse.from(service.setActive(id, false), true);
    }
}
