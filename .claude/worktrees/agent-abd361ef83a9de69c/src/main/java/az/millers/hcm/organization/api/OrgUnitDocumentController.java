package az.millers.hcm.organization.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.organization.api.dto.OrgUnitDocumentDtos.OrgUnitDocumentRequest;
import az.millers.hcm.organization.api.dto.OrgUnitDocumentDtos.OrgUnitDocumentResponse;
import az.millers.hcm.organization.service.OrgUnitDocumentService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * M147 / §31 — document registry for org units.
 *
 * <p>Read access: all HR roles (same gate as the org structure read endpoints).
 * Write access: HR admin / specialist.
 */
@RestController
@RequestMapping("/api/org/units/{unitId}/documents")
public class OrgUnitDocumentController {

    private final OrgUnitDocumentService service;

    public OrgUnitDocumentController(OrgUnitDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<OrgUnitDocumentResponse> list(@PathVariable UUID unitId) {
        return service.list(unitId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public OrgUnitDocumentResponse create(@PathVariable UUID unitId,
                                           @Valid @RequestBody OrgUnitDocumentRequest req) {
        return service.create(unitId, req);
    }

    @PutMapping("/{docId}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public OrgUnitDocumentResponse update(@PathVariable UUID unitId,
                                           @PathVariable UUID docId,
                                           @Valid @RequestBody OrgUnitDocumentRequest req) {
        return service.update(unitId, docId, req);
    }

    @DeleteMapping("/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID unitId, @PathVariable UUID docId) {
        service.delete(unitId, docId);
    }
}
