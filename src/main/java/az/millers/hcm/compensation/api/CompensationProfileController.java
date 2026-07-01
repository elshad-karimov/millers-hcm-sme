package az.millers.hcm.compensation.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.api.dto.CompensationProfileDto;
import az.millers.hcm.compensation.service.CompensationProfileService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M360 — Employee compensation profile endpoint.
 */
@RestController
@RequestMapping("/api/compensation/employees")
public class CompensationProfileController {

    private final CompensationProfileService service;

    public CompensationProfileController(CompensationProfileService service) {
        this.service = service;
    }

    @GetMapping("/{employeeId}/profile")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public CompensationProfileDto profile(@PathVariable UUID employeeId) {
        return service.profile(employeeId);
    }
}
