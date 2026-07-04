package az.millers.hcm.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for the {@code hcm.keycloak.admin.*} block in application.yml.
 * Allows KeycloakAdminService to talk to the Keycloak Admin REST API via a
 * short-lived admin token obtained from the master realm's admin-cli client.
 */
@ConfigurationProperties(prefix = "hcm.keycloak.admin")
public record KeycloakAdminProperties(
        String serverUrl,
        String realm,
        String masterRealm,
        String adminClientId,
        String username,
        String password
) {
    /** Fallback-safe accessors so tests / partial configs don't NPE. */
    public String effectiveMasterRealm() {
        return masterRealm != null ? masterRealm : "master";
    }
    public String effectiveAdminClientId() {
        return adminClientId != null ? adminClientId : "admin-cli";
    }
}
