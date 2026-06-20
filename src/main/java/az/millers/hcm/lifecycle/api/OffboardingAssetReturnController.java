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

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.AssetReturnResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.AssetReturnUpdateRequest;
import az.millers.hcm.lifecycle.service.OffboardingAssetReturnService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/lifecycle/offboarding/cases/{caseId}/assets")
public class OffboardingAssetReturnController {

    private final OffboardingAssetReturnService service;

    public OffboardingAssetReturnController(OffboardingAssetReturnService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AssetReturnResponse> list(@PathVariable UUID caseId) {
        return service.getReturns(caseId);
    }

    @PatchMapping("/{returnId}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public AssetReturnResponse update(@PathVariable UUID caseId,
                                      @PathVariable UUID returnId,
                                      @RequestBody AssetReturnUpdateRequest request) {
        return service.updateReturn(returnId, request);
    }
}
