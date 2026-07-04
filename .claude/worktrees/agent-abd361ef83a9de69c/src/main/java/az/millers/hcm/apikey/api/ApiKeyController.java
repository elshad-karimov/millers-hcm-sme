package az.millers.hcm.apikey.api;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.apikey.api.ApiKeyDtos.ApiKeySummary;
import az.millers.hcm.apikey.api.ApiKeyDtos.IssueRequest;
import az.millers.hcm.apikey.api.ApiKeyDtos.IssueResponse;
import az.millers.hcm.apikey.api.ApiKeyDtos.RevokeRequest;
import az.millers.hcm.apikey.api.ApiKeyDtos.UsageResponse;
import az.millers.hcm.apikey.service.ApiKeyService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M120 — admin surface for issuing and managing integration API keys.
 *
 * <p>Issuing / revoking is {@code SYSTEM_ADMIN} only — the credential
 * grants role authorities, so creating one is privilege escalation by
 * other means. The read surface is widened to HR admins so they can
 * see what integrations exist without being able to mint new ones.
 */
@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {

    private final ApiKeyService service;

    public ApiKeyController(ApiKeyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('" + SecurityRoles.R_SYSTEM_ADMIN + "','" + SecurityRoles.R_HR_ADMIN + "','" + SecurityRoles.R_AUDITOR + "')")
    public List<ApiKeySummary> list() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('" + SecurityRoles.R_SYSTEM_ADMIN + "','" + SecurityRoles.R_HR_ADMIN + "','" + SecurityRoles.R_AUDITOR + "')")
    public ApiKeySummary get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/usage")
    @PreAuthorize("hasAnyRole('" + SecurityRoles.R_SYSTEM_ADMIN + "','" + SecurityRoles.R_HR_ADMIN + "','" + SecurityRoles.R_AUDITOR + "')")
    public UsageResponse usage(@PathVariable UUID id,
                               @RequestParam(defaultValue = "24") int hours) {
        return service.usage(id, hours);
    }

    @PostMapping
    @PreAuthorize("hasRole('" + SecurityRoles.R_SYSTEM_ADMIN + "')")
    public IssueResponse issue(@RequestBody IssueRequest req) {
        return service.issue(req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('" + SecurityRoles.R_SYSTEM_ADMIN + "')")
    public ResponseEntity<ApiKeySummary> revoke(@PathVariable UUID id,
                                                @RequestBody(required = false) RevokeRequest req) {
        return ResponseEntity.ok(service.revoke(id, req == null ? null : req.reason()));
    }
}
