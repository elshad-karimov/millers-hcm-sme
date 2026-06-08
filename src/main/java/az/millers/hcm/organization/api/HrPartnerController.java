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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.organization.api.dto.HrPartnerDtos.HrPartnerRequest;
import az.millers.hcm.organization.api.dto.HrPartnerDtos.HrPartnerResponse;
import az.millers.hcm.organization.service.HrPartnerService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * M142 — admin surface for HRBP assignments (§24).
 *
 * Read: any HR + managers. Write: HR_ADMIN / SYSTEM_ADMIN.
 */
@RestController
@RequestMapping("/api/hr-partners")
public class HrPartnerController {

    private final HrPartnerService service;

    public HrPartnerController(HrPartnerService service) {
        this.service = service;
    }

    /** All active assignments for a given org unit. */
    @GetMapping("/by-unit/{orgUnitId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<HrPartnerResponse> listForUnit(@PathVariable UUID orgUnitId) {
        return service.listForUnit(orgUnitId).stream()
                .map(HrPartnerResponse::from).toList();
    }

    /** All assignments for a given HRBP employee. */
    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<HrPartnerResponse> listForEmployee(@PathVariable UUID employeeId) {
        return service.listForEmployee(employeeId).stream()
                .map(HrPartnerResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public HrPartnerResponse get(@PathVariable UUID id) {
        return HrPartnerResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public HrPartnerResponse create(@Valid @RequestBody HrPartnerRequest req) {
        return HrPartnerResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public HrPartnerResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody HrPartnerRequest req) {
        return HrPartnerResponse.from(service.update(id, req));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public HrPartnerResponse activate(@PathVariable UUID id) {
        return HrPartnerResponse.from(service.setActive(id, true));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public HrPartnerResponse deactivate(@PathVariable UUID id) {
        return HrPartnerResponse.from(service.setActive(id, false));
    }
}
