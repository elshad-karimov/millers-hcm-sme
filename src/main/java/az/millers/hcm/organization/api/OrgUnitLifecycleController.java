package az.millers.hcm.organization.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.organization.api.dto.OrgUnitLifecycleDtos.ClosureRequest;
import az.millers.hcm.organization.api.dto.OrgUnitLifecycleDtos.ReopenRequest;
import az.millers.hcm.organization.api.dto.OrgUnitResponse;
import az.millers.hcm.organization.service.OrgUnitLifecycleService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M144 — lifecycle state transitions for org units (§26).
 *
 * All write operations require HR_ADMIN or SYSTEM_ADMIN.
 * Transitions bypass the DRAFT-version constraint so they can be applied
 * to units in the active structure version.
 */
@RestController
@RequestMapping("/api/org/units/{id}")
public class OrgUnitLifecycleController {

    private final OrgUnitLifecycleService service;

    public OrgUnitLifecycleController(OrgUnitLifecycleService service) {
        this.service = service;
    }

    /** PLANNED → ACTIVE. */
    @PostMapping("/open")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OrgUnitResponse open(@PathVariable UUID id) {
        return OrgUnitResponse.from(service.open(id));
    }

    /** ACTIVE → CLOSING. */
    @PostMapping("/announce-closure")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OrgUnitResponse announceClosure(@PathVariable UUID id,
                                            @RequestBody(required = false) ClosureRequest req) {
        return OrgUnitResponse.from(service.announceClosure(id,
                req != null ? req : new ClosureRequest(null, null)));
    }

    /** CLOSING → ACTIVE. */
    @PostMapping("/cancel-closure")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OrgUnitResponse cancelClosure(@PathVariable UUID id) {
        return OrgUnitResponse.from(service.cancelClosure(id));
    }

    /** CLOSING or ACTIVE → CLOSED. */
    @PostMapping("/close")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OrgUnitResponse close(@PathVariable UUID id,
                                  @RequestBody(required = false) ClosureRequest req) {
        return OrgUnitResponse.from(service.close(id,
                req != null ? req : new ClosureRequest(null, null)));
    }

    /** CLOSED → ACTIVE. */
    @PostMapping("/reopen")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OrgUnitResponse reopen(@PathVariable UUID id,
                                   @RequestBody(required = false) ReopenRequest req) {
        return OrgUnitResponse.from(service.reopen(id, req));
    }
}
