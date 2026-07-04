package az.millers.hcm.organization.api;

import java.util.List;

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

import az.millers.hcm.organization.api.dto.OrgUnitTypeConfigDtos.OrgUnitTypeConfigRequest;
import az.millers.hcm.organization.api.dto.OrgUnitTypeConfigDtos.OrgUnitTypeConfigResponse;
import az.millers.hcm.organization.service.OrgUnitTypeConfigService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * M143 — admin surface for the org-unit type config registry (§5).
 *
 * Read: any HR + managers. Write: HR_ADMIN / SYSTEM_ADMIN.
 */
@RestController
@RequestMapping("/api/org-unit-types")
public class OrgUnitTypeConfigController {

    private final OrgUnitTypeConfigService service;

    public OrgUnitTypeConfigController(OrgUnitTypeConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<OrgUnitTypeConfigResponse> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly).stream()
                .map(OrgUnitTypeConfigResponse::from).toList();
    }

    @GetMapping("/{code}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public OrgUnitTypeConfigResponse get(@PathVariable String code) {
        return OrgUnitTypeConfigResponse.from(service.get(code));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OrgUnitTypeConfigResponse create(@Valid @RequestBody OrgUnitTypeConfigRequest req) {
        return OrgUnitTypeConfigResponse.from(service.create(req));
    }

    @PutMapping("/{code}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OrgUnitTypeConfigResponse update(@PathVariable String code,
                                             @Valid @RequestBody OrgUnitTypeConfigRequest req) {
        return OrgUnitTypeConfigResponse.from(service.update(code, req));
    }

    @PostMapping("/{code}/activate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OrgUnitTypeConfigResponse activate(@PathVariable String code) {
        return OrgUnitTypeConfigResponse.from(service.setActive(code, true));
    }

    @PostMapping("/{code}/deactivate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OrgUnitTypeConfigResponse deactivate(@PathVariable String code) {
        return OrgUnitTypeConfigResponse.from(service.setActive(code, false));
    }
}
