package az.millers.hcm.ldap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed binding for the {@code hcm.ldap.*} block in {@code application.yml}.
 *
 * <p>Controls how {@link LdapFederationService} connects to OpenLDAP and
 * configures the Keycloak LDAP user-storage federation provider at startup.
 *
 * <p>All fields have sensible defaults matching the {@code hcm-openldap}
 * Docker container; override via environment variables in production.
 */
@ConfigurationProperties(prefix = "hcm.ldap")
public record LdapProperties(

        /** LDAP connection URL reachable from the Keycloak container. */
        @DefaultValue("ldap://hcm-openldap:389")
        String connectionUrl,

        /** DN of the LDAP service account used by Keycloak to search the directory. */
        @DefaultValue("cn=admin,dc=millers,dc=az")
        String bindDn,

        /** Password for the LDAP service account. */
        @DefaultValue("adminpassword")
        String bindCredential,

        /** DN of the OU containing user entries (mapped to Keycloak users). */
        @DefaultValue("ou=users,dc=millers,dc=az")
        String usersDn,

        /** DN of the OU containing group entries (mapped to Keycloak realm roles). */
        @DefaultValue("ou=groups,dc=millers,dc=az")
        String groupsDn,

        /**
         * Keycloak name for the LDAP user-storage component.
         * Must be stable across restarts because idempotency is checked by name.
         */
        @DefaultValue("millers-ldap")
        String providerName,

        /**
         * When {@code true} (default), {@link LdapSetupRunner} creates the LDAP
         * federation provider in Keycloak automatically at startup.
         * Set to {@code false} to disable auto-setup and configure manually via
         * {@code POST /api/admin/ldap/setup}.
         */
        @DefaultValue("true")
        boolean autoSetup
) {}
