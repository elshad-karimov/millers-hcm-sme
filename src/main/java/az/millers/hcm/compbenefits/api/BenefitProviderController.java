package az.millers.hcm.compbenefits.api;

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

import az.millers.hcm.compbenefits.api.dto.BenefitProviderDtos.ProviderRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitProviderDtos.ProviderResponse;
import az.millers.hcm.compbenefits.service.BenefitProviderService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * Benefit provider / vendor master API (HCM_11 M374). Reads for benefits/HR readers;
 * writes gated to benefits/HR admin.
 */
@RestController
@RequestMapping("/api/compbenefits/benefit-providers")
public class BenefitProviderController {

    private final BenefitProviderService service;

    public BenefitProviderController(BenefitProviderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_BENEFITS)
    public List<ProviderResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_BENEFITS)
    public ProviderResponse get(@PathVariable UUID id) {
        return ProviderResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public ProviderResponse create(@Valid @RequestBody ProviderRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public ProviderResponse update(@PathVariable UUID id, @Valid @RequestBody ProviderRequest req) {
        return service.update(id, req);
    }
}
