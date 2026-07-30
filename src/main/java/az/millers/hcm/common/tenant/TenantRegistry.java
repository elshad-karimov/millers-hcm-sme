package az.millers.hcm.common.tenant;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * In-memory cache of the {@link Tenant} registry (multi-tenancy Phase 3).
 *
 * <p>Answers two questions on the hot path of every authenticated request:
 * <ol>
 *   <li>"Is this JWT issuer trusted?" — the multi-issuer resource-server
 *       resolver only builds a decoder for issuers that map to an active tenant.</li>
 *   <li>"Which tenant does this issuer belong to?" — the
 *       {@code TenantResolutionFilter} maps the authenticated token's {@code iss}
 *       to a tenant id and binds {@link TenantContext}.</li>
 * </ol>
 *
 * <p>The cache is loaded lazily on first use and refreshed explicitly by the
 * provisioning service when a tenant is added ({@link #refresh()}). Reads are
 * lock-free against a volatile snapshot.
 */
@Service
public class TenantRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistry.class);

    private final TenantRepository repo;

    /** issuer_uri -> tenant id, for active tenants only. Replaced atomically. */
    private volatile Map<String, String> issuerToTenant = Map.of();
    private volatile boolean loaded = false;

    public TenantRegistry(TenantRepository repo) {
        this.repo = repo;
    }

    /** Rebuild the snapshot from the database. Call after provisioning a tenant. */
    public synchronized void refresh() {
        Map<String, String> snapshot = repo.findByActiveTrue().stream()
                .collect(Collectors.toMap(Tenant::getIssuerUri, Tenant::getId, (a, b) -> a));
        this.issuerToTenant = Map.copyOf(snapshot);
        this.loaded = true;
        log.info("TenantRegistry refreshed: {} active tenant(s) — issuers {}",
                snapshot.size(), snapshot.keySet());
    }

    private Map<String, String> snapshot() {
        if (!loaded) {
            refresh();
        }
        return issuerToTenant;
    }

    /** All trusted JWT issuers (active tenants). */
    public Set<String> trustedIssuers() {
        return snapshot().keySet();
    }

    public boolean isTrustedIssuer(String issuerUri) {
        return snapshot().containsKey(issuerUri);
    }

    /** Resolve a JWT issuer to its tenant id, if trusted. */
    public Optional<String> tenantForIssuer(String issuerUri) {
        return Optional.ofNullable(snapshot().get(issuerUri));
    }
}
