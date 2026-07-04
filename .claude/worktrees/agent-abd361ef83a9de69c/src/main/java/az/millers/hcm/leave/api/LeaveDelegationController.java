package az.millers.hcm.leave.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.api.dto.LeaveDelegationRequest;
import az.millers.hcm.leave.api.dto.LeaveDelegationResponse;
import az.millers.hcm.leave.service.LeaveDelegationService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave")
public class LeaveDelegationController {

    private final LeaveDelegationService service;

    public LeaveDelegationController(LeaveDelegationService service) {
        this.service = service;
    }

    /** All delegations for a leave request. */
    @GetMapping("/requests/{requestId}/delegations")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public List<LeaveDelegationResponse> list(@PathVariable UUID requestId) {
        return service.listForRequest(requestId);
    }

    /** Pending delegations where the given employee is the delegate (self-service inbox). */
    @GetMapping("/delegations/pending")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<LeaveDelegationResponse> pending(@RequestParam UUID delegateId) {
        return service.listPendingForDelegate(delegateId);
    }

    /** Create a delegation for a leave request. */
    @PostMapping("/requests/{requestId}/delegations")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public LeaveDelegationResponse create(@PathVariable UUID requestId,
                                          @Valid @RequestBody LeaveDelegationRequest req) {
        return service.create(requestId, req);
    }

    /** Delegate accepts coverage. */
    @PostMapping("/delegations/{id}/accept")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public LeaveDelegationResponse accept(@PathVariable UUID id,
                                          @RequestParam(required = false) String notes) {
        return service.accept(id, notes);
    }

    /** Delegate declines coverage. */
    @PostMapping("/delegations/{id}/decline")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public LeaveDelegationResponse decline(@PathVariable UUID id,
                                           @RequestParam(required = false) String notes) {
        return service.decline(id, notes);
    }

    /** Requester revokes the delegation. */
    @DeleteMapping("/delegations/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public LeaveDelegationResponse revoke(@PathVariable UUID id) {
        return service.revoke(id);
    }
}
