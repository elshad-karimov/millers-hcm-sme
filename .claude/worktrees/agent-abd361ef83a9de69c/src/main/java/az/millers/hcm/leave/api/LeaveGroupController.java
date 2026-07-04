package az.millers.hcm.leave.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

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

import az.millers.hcm.leave.api.dto.LeaveGroupEntitlementRequest;
import az.millers.hcm.leave.api.dto.LeaveGroupEntitlementResponse;
import az.millers.hcm.leave.api.dto.LeaveGroupRequest;
import az.millers.hcm.leave.api.dto.LeaveGroupResponse;
import az.millers.hcm.leave.service.LeaveGroupService;

/**
 * REST surface for {@link az.millers.hcm.leave.domain.LeaveGroup} +
 * per-(group, type) entitlement overrides (M66 / P1-08).
 *
 * <p>HR_ADMIN / SYSTEM_ADMIN write everything; HR_SPECIALIST and AUDITOR can
 * read for transparency. Entitlement upsert lives as a child resource so the
 * URL reflects the (group, type) composite key.
 */
@RestController
@RequestMapping("/api/leave/groups")
public class LeaveGroupController {

    /** Centralised role sets — see {@link az.millers.hcm.security.SecurityRoles}. */
    private static final String READ_ROLES = az.millers.hcm.security.SecurityRoles.READ_HR;
    private static final String WRITE_ROLES = az.millers.hcm.security.SecurityRoles.WRITE_HR_ADMIN_ONLY;

    private final LeaveGroupService service;

    public LeaveGroupController(LeaveGroupService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<LeaveGroupResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public LeaveGroupResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public LeaveGroupResponse create(@RequestBody @Valid LeaveGroupRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public LeaveGroupResponse update(@PathVariable UUID id,
                                      @RequestBody @Valid LeaveGroupRequest req) {
        return service.update(id, req);
    }

    // ── Per-(group, type) entitlement overrides ───────────────────────────────

    @GetMapping("/{id}/entitlements")
    @PreAuthorize(READ_ROLES)
    public List<LeaveGroupEntitlementResponse> listEntitlements(@PathVariable UUID id) {
        return service.listEntitlements(id);
    }

    @PutMapping("/{id}/entitlements")
    @PreAuthorize(WRITE_ROLES)
    public LeaveGroupEntitlementResponse upsertEntitlement(
            @PathVariable UUID id,
            @RequestBody @Valid LeaveGroupEntitlementRequest req) {
        return service.upsertEntitlement(id, req);
    }

    @DeleteMapping("/{id}/entitlements/{leaveTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteEntitlement(@PathVariable UUID id, @PathVariable UUID leaveTypeId) {
        service.deleteEntitlement(id, leaveTypeId);
    }
}
