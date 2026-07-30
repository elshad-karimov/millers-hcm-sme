package az.millers.hcm.common.tenant;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;

/**
 * Tenant administration (multi-tenancy Phase 4). SYSTEM_ADMIN only — this is a
 * cross-tenant control-plane surface, not a per-tenant business API.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class TenantAdminController {

    private final TenantRepository tenants;
    private final TenantProvisioningService provisioning;
    private final az.millers.hcm.security.CurrentRequest currentRequest;

    public TenantAdminController(TenantRepository tenants, TenantProvisioningService provisioning,
                                 az.millers.hcm.security.CurrentRequest currentRequest) {
        this.tenants = tenants;
        this.provisioning = provisioning;
        this.currentRequest = currentRequest;
    }

    public record TenantView(String id, String name, String issuerUri, String realm, boolean active) {
        static TenantView of(Tenant t) {
            return new TenantView(t.getId(), t.getName(), t.getIssuerUri(), t.getRealm(), t.isActive());
        }
    }

    public record ProvisionRequest(
            @NotBlank String id,
            @NotBlank String name,
            @NotBlank String issuerUri,
            String realm,
            String seedFromTenant) {}

    @GetMapping
    public List<TenantView> list() {
        return tenants.findAll().stream().map(TenantView::of).toList();
    }

    @PostMapping
    public TenantProvisioningService.ProvisionResult provision(@RequestBody ProvisionRequest req) {
        String actor = currentRequest.username();
        return provisioning.provision(req.id(), req.name(), req.issuerUri(),
                req.realm(), req.seedFromTenant(), actor);
    }
}
