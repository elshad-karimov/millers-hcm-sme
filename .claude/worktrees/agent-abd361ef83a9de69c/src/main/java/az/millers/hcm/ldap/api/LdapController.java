package az.millers.hcm.ldap.api;

import az.millers.hcm.ldap.LdapFederationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin REST endpoints for LDAP/AD federation management (M54 — PRD §14.6).
 *
 * <p>All endpoints require the {@code SYSTEM_ADMIN} realm role.
 *
 * <ul>
 *   <li>{@code GET  /api/admin/ldap/status}      — current provider state</li>
 *   <li>{@code POST /api/admin/ldap/setup}        — idempotent provider creation</li>
 *   <li>{@code POST /api/admin/ldap/sync?type=full|changed} — trigger a sync</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/ldap")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class LdapController {

    private final LdapFederationService service;

    public LdapController(LdapFederationService service) {
        this.service = service;
    }

    /**
     * Returns the current state of the Keycloak LDAP federation provider.
     *
     * @return provider status snapshot; {@code setupStatus = "not_configured"}
     *         when no provider exists or Keycloak is unreachable.
     */
    @GetMapping("/status")
    public LdapStatusResponse status() {
        return service.getStatus();
    }

    /**
     * Creates or verifies the LDAP federation provider in Keycloak.
     *
     * <p>Safe to call multiple times — the operation is idempotent.
     * Returns {@code 202 Accepted} immediately; the setup runs synchronously
     * on the calling thread (callers should expect a few seconds of latency
     * on first call while Keycloak creates the component and runs an initial
     * full sync).
     */
    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void setup() {
        service.setupIfNeeded();
    }

    /**
     * Triggers an LDAP synchronisation.
     *
     * @param type {@code "full"} (default) — import all LDAP users;
     *             {@code "changed"} — import only users modified since the last sync.
     * @return counters of users added / updated / removed / failed
     */
    @PostMapping("/sync")
    public LdapSyncResult sync(@RequestParam(defaultValue = "full") String type) {
        return "changed".equals(type) ? service.syncChanged() : service.syncFull();
    }
}
