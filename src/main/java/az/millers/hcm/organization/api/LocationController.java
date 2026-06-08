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

import az.millers.hcm.organization.api.dto.LocationDtos.LocationRequest;
import az.millers.hcm.organization.api.dto.LocationDtos.LocationResponse;
import az.millers.hcm.organization.service.LocationService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * M141 — admin surface for the Location master (§11).
 *
 * Read access: any HR + managers (same gate as Legal Entity).
 * Write access: HR_ADMIN / SYSTEM_ADMIN only.
 */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<LocationResponse> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly).stream()
                .map(LocationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public LocationResponse get(@PathVariable UUID id) {
        return LocationResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LocationResponse create(@Valid @RequestBody LocationRequest req) {
        return LocationResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LocationResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody LocationRequest req) {
        return LocationResponse.from(service.update(id, req));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LocationResponse activate(@PathVariable UUID id) {
        return LocationResponse.from(service.setActive(id, true));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LocationResponse deactivate(@PathVariable UUID id) {
        return LocationResponse.from(service.setActive(id, false));
    }
}
