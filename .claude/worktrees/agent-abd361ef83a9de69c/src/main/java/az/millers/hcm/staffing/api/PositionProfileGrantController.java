package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionProfileGrantDtos.GrantResponse;
import az.millers.hcm.staffing.api.dto.PositionProfileGrantDtos.ReasonRequest;
import az.millers.hcm.staffing.service.PositionProfileGrantService;
import jakarta.validation.Valid;

/** M250 — REST surface for profile grants per occupancy / employee. */
@RestController
@RequestMapping("/api/position-profile-grants")
public class PositionProfileGrantController {

    private final PositionProfileGrantService service;

    public PositionProfileGrantController(PositionProfileGrantService service) {
        this.service = service;
    }

    @GetMapping("/by-occupancy/{occupancyId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','MANAGER','DEPARTMENT_MANAGER')")
    public List<GrantResponse> byOccupancy(@PathVariable UUID occupancyId) {
        return service.forOccupancy(occupancyId).stream().map(GrantResponse::from).toList();
    }

    @GetMapping("/pending/{employeeId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','MANAGER','DEPARTMENT_MANAGER')")
    public List<GrantResponse> pendingForEmployee(@PathVariable UUID employeeId) {
        return service.pendingForEmployee(employeeId).stream().map(GrantResponse::from).toList();
    }

    @PostMapping("/{id}/mark-active")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public GrantResponse markActive(@PathVariable UUID id) {
        return GrantResponse.from(service.markActive(id));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public GrantResponse revoke(@PathVariable UUID id,
                                 @Valid @RequestBody(required = false) ReasonRequest req) {
        return GrantResponse.from(service.revoke(id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/mark-failed")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public GrantResponse markFailed(@PathVariable UUID id,
                                     @Valid @RequestBody ReasonRequest req) {
        return GrantResponse.from(service.markFailed(id, req.reason()));
    }
}
