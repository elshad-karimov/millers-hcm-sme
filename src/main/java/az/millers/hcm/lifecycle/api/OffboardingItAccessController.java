package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ItAccessResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ItAccessUpdateRequest;
import az.millers.hcm.lifecycle.service.OffboardingItAccessService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/lifecycle/offboarding/cases/{caseId}/it-access")
public class OffboardingItAccessController {

    private final OffboardingItAccessService service;

    public OffboardingItAccessController(OffboardingItAccessService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ItAccessResponse> list(@PathVariable UUID caseId) {
        return service.getAccess(caseId);
    }

    @PatchMapping("/{accessId}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ItAccessResponse update(@PathVariable UUID caseId,
                                   @PathVariable UUID accessId,
                                   @RequestBody ItAccessUpdateRequest request) {
        return service.update(accessId, request);
    }
}
