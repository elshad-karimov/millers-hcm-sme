package az.millers.hcm.preboarding.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.preboarding.api.PreboardingDtos.CompleteRequest;
import az.millers.hcm.preboarding.api.PreboardingDtos.InviteDetail;
import az.millers.hcm.preboarding.api.PreboardingDtos.InviteSummary;
import az.millers.hcm.preboarding.api.PreboardingDtos.IssueRequest;
import az.millers.hcm.preboarding.api.PreboardingDtos.IssueResponse;
import az.millers.hcm.preboarding.api.PreboardingDtos.RevokeRequest;
import az.millers.hcm.preboarding.service.PreboardingInviteService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M122 — HR-facing surface. The public candidate surface lives in
 * {@link PublicPreboardingController}; this one is JWT-protected and
 * gated to HR_ADMIN / HR_SPECIALIST.
 */
@RestController
@RequestMapping("/api/preboarding/invites")
@PreAuthorize(SecurityRoles.WRITE_HR)
public class PreboardingInviteController {

    private final PreboardingInviteService service;

    public PreboardingInviteController(PreboardingInviteService service) {
        this.service = service;
    }

    @GetMapping
    public List<InviteSummary> list() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    public InviteDetail get(@PathVariable UUID id) {
        return service.getDetail(id);
    }

    @PostMapping
    public IssueResponse issue(@RequestBody IssueRequest req) {
        return service.issue(req);
    }

    @PostMapping("/{id}/complete")
    public InviteSummary complete(@PathVariable UUID id, @RequestBody(required = false) CompleteRequest req) {
        return service.complete(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<InviteSummary> revoke(@PathVariable UUID id,
                                                @RequestBody(required = false) RevokeRequest req) {
        return ResponseEntity.ok(service.revoke(id, req == null ? null : req.reason()));
    }
}
