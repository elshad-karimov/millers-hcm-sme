package az.millers.hcm.ldap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import az.millers.hcm.admin.KeycloakAdminService;
import az.millers.hcm.common.UpstreamServiceException;

/**
 * Registering an LDAP directory that isn't there.
 *
 * <p>Keycloak answers a realm's user list from every user-storage provider it
 * has been told about, and one provider it cannot reach fails the whole query —
 * 400 {@code unknown_error}, no users at all, not even the ones held locally.
 * A deployment with no LDAP box therefore must not end up with an LDAP provider
 * registered, or its entire user-administration screen goes down for a
 * directory it never had.
 */
class LdapFederationSetupGuardTest {

    private static LdapProperties propsFor(String connectionUrl) {
        return new LdapProperties(connectionUrl,
                "cn=admin,dc=millers,dc=az", "adminpassword",
                "ou=users,dc=millers,dc=az", "ou=groups,dc=millers,dc=az",
                "millers-ldap", true);
    }

    private static LdapFederationService serviceFor(String connectionUrl,
                                                     KeycloakAdminService keycloak) {
        return new LdapFederationService(propsFor(connectionUrl), keycloak, RestClient.builder());
    }

    @Test
    @DisplayName("a directory that resolves nowhere is never registered with Keycloak")
    void unreachableHostIsNotRegistered() {
        KeycloakAdminService keycloak = mock(KeycloakAdminService.class);
        LdapFederationService service =
                serviceFor("ldap://ldap-host-that-does-not-exist.invalid:389", keycloak);

        assertThatThrownBy(service::setupIfNeeded)
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("not reachable")
                .hasMessageContaining("hcm.ldap.auto-setup=false");

        // The point of the guard: Keycloak is never touched, so no provider is
        // left behind to break the realm's user list.
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("a host that resolves but refuses the port is treated the same")
    void closedPortIsNotRegistered() throws IOException {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        } // closed on exit — nothing is listening there now

        KeycloakAdminService keycloak = mock(KeycloakAdminService.class);
        LdapFederationService service = serviceFor("ldap://127.0.0.1:" + closedPort, keycloak);

        assertThatThrownBy(service::setupIfNeeded)
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("not reachable");
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("a directory that answers gets past the guard and on to Keycloak")
    void reachableDirectoryProceeds() throws IOException {
        try (ServerSocket listening = new ServerSocket(0)) {
            KeycloakAdminService keycloak = mock(KeycloakAdminService.class);
            LdapFederationService service =
                    serviceFor("ldap://127.0.0.1:" + listening.getLocalPort(), keycloak);

            // Past the probe the mocked Keycloak does nothing useful, and
            // setupIfNeeded is documented to swallow Keycloak-side failures —
            // so "no throw" is exactly the assertion: the guard let it through.
            assertThatCode(service::setupIfNeeded).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("no configured URL at all is reported, not dereferenced")
    void missingUrlIsRejected() {
        KeycloakAdminService keycloak = mock(KeycloakAdminService.class);
        LdapFederationService service = serviceFor(null, keycloak);

        assertThatThrownBy(service::setupIfNeeded)
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("No LDAP connection URL is configured");
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("a connection URL with no host is rejected, not silently probed")
    void urlWithoutHostIsRejected() {
        KeycloakAdminService keycloak = mock(KeycloakAdminService.class);
        LdapFederationService service = serviceFor("not-a-url", keycloak);

        assertThatThrownBy(service::setupIfNeeded)
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("no host");
        verifyNoInteractions(keycloak);
    }

}
