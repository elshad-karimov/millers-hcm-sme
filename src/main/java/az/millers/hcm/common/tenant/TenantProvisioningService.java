package az.millers.hcm.common.tenant;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Provisions a new tenant (multi-tenancy Phase 4).
 *
 * <p>One transactional operation: register the tenant (issuer → id mapping),
 * clone the reference/config data it needs to be functional, and refresh the
 * {@link TenantRegistry} so the new realm's tokens are trusted immediately —
 * no restart.
 *
 * <p>Out of scope (request-tracking / operational): creating the Keycloak realm
 * itself. That is an identity-plane action performed via the Keycloak admin API
 * or realm import; this service records the realm's issuer and wires the data
 * plane. Employee/transactional data is never seeded — a new tenant starts with
 * people onboarded through the normal flows.
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

    private final TenantRepository tenants;
    private final TenantReferenceSeeder seeder;
    private final TenantRegistry registry;

    public TenantProvisioningService(TenantRepository tenants,
                                     TenantReferenceSeeder seeder,
                                     TenantRegistry registry) {
        this.tenants = tenants;
        this.seeder = seeder;
        this.registry = registry;
    }

    public record ProvisionResult(String tenantId, Map<String, Integer> referenceCounts) {}

    /**
     * Create a tenant, clone reference data from {@code seedFromTenant}, and
     * refresh the registry. Idempotent guard: fails fast if the id or issuer is
     * already registered.
     */
    @Transactional
    public ProvisionResult provision(String tenantId, String name, String issuerUri,
                                     String realm, String seedFromTenant, String actor) {
        if (tenants.existsById(tenantId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant already exists: " + tenantId);
        }
        if (tenants.existsByIssuerUri(issuerUri)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Issuer already registered: " + issuerUri);
        }

        Tenant tenant = new Tenant(tenantId, name, issuerUri, realm);
        tenant.setCreatedBy(actor);
        tenant.setUpdatedBy(actor);
        tenants.save(tenant);

        String source = (seedFromTenant == null || seedFromTenant.isBlank())
                ? TenantContext.DEFAULT : seedFromTenant;
        Map<String, Integer> counts = seeder.copyReferenceData(source, tenantId);

        // Make the new issuer trusted + resolvable right away.
        registry.refresh();

        log.info("Provisioned tenant '{}' (realm={}, issuer={}) by {}; reference rows {}",
                tenantId, realm, issuerUri, actor, counts.values().stream().mapToInt(Integer::intValue).sum());
        return new ProvisionResult(tenantId, counts);
    }
}
