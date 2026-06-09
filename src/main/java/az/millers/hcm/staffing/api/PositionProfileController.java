package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionProfileDtos.CloneFromRequest;
import az.millers.hcm.staffing.api.dto.PositionProfileDtos.GrantPreviewResponse;
import az.millers.hcm.staffing.api.dto.PositionProfileDtos.ProfileItemRequest;
import az.millers.hcm.staffing.api.dto.PositionProfileDtos.ProfileItemResponse;
import az.millers.hcm.staffing.service.PositionProfileService;
import jakarta.validation.Valid;

/** M248 — REST surface for the position profile (PRD §25–§29). */
@RestController
@RequestMapping("/api/positions/{positionId}/profile")
public class PositionProfileController {

    private final PositionProfileService service;

    public PositionProfileController(PositionProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','FINANCE_USER','MANAGER','DEPARTMENT_MANAGER')")
    public List<ProfileItemResponse> list(@PathVariable UUID positionId) {
        return service.forPosition(positionId).stream()
                .map(ProfileItemResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ProfileItemResponse create(@PathVariable UUID positionId,
                                       @Valid @RequestBody ProfileItemRequest req) {
        return ProfileItemResponse.from(service.create(positionId, req.toEntity()));
    }

    @PutMapping("/{itemId}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ProfileItemResponse update(@PathVariable UUID positionId,
                                       @PathVariable UUID itemId,
                                       @Valid @RequestBody ProfileItemRequest req) {
        return ProfileItemResponse.from(service.update(itemId, req.toEntity()));
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID positionId, @PathVariable UUID itemId) {
        service.delete(itemId);
    }

    @PostMapping("/clone-from")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public List<ProfileItemResponse> cloneFrom(@PathVariable UUID positionId,
                                                @Valid @RequestBody CloneFromRequest req) {
        return service.cloneFrom(req.sourcePositionId(), positionId).stream()
                .map(ProfileItemResponse::from).toList();
    }

    /**
     * Preview which grants would be created for {@code employeeId} if they
     * occupied this position. Phase F = preview only; Phase F.2 will turn
     * this into an actual auto-grant on
     * {@link az.millers.hcm.staffing.service.PositionOccupancyService#create}.
     */
    @GetMapping("/grant-preview/{employeeId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','MANAGER','DEPARTMENT_MANAGER')")
    public List<GrantPreviewResponse> grantPreview(@PathVariable UUID positionId,
                                                     @PathVariable UUID employeeId) {
        return service.grantPreviewFor(positionId, employeeId).stream()
                .map(GrantPreviewResponse::from).toList();
    }
}
