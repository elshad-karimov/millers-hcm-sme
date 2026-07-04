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

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ClearanceResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ClearanceSignOffRequest;
import az.millers.hcm.lifecycle.domain.ClearanceDepartment;
import az.millers.hcm.lifecycle.service.OffboardingClearanceService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/lifecycle/offboarding/cases/{caseId}/clearance")
public class OffboardingClearanceController {

    private final OffboardingClearanceService service;

    public OffboardingClearanceController(OffboardingClearanceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ClearanceResponse> list(@PathVariable UUID caseId) {
        return service.getClearances(caseId);
    }

    @PatchMapping("/{department}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ClearanceResponse signOff(
            @PathVariable UUID caseId,
            @PathVariable ClearanceDepartment department,
            @RequestBody ClearanceSignOffRequest request) {
        return service.signOff(caseId, department, request);
    }
}
