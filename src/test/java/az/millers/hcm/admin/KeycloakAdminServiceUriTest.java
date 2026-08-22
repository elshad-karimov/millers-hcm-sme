package az.millers.hcm.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Usernames in this system are email addresses, and an email address is the one
 * value that exposes double-encoding: RestClient encodes the URI template it is
 * given, so a value pre-encoded with URLEncoder is encoded a second time and
 * {@code %40} becomes {@code %2540}.
 *
 * <p>That is not a cosmetic difference. Creating a login POSTed successfully and
 * then looked the user up to get its id; the lookup asked Keycloak for a user
 * literally named "abbas.abbasli%40saipem.com", got an empty array, and the
 * service reported that the account it had just created could not be found —
 * after having created it. The employee ended up with no username stored and an
 * orphaned Keycloak account.
 */
class KeycloakAdminServiceUriTest {

    private static final String SERVER = "http://keycloak:8090";
    private static final String REALM = "millers-hcm";

    @Test
    @DisplayName("an email username is encoded once, so Keycloak can find it")
    void emailUsernameIsEncodedExactlyOnce() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService service = new KeycloakAdminService(props(), builder);

        // The token call comes first; the service caches it for the lookup.
        server.expect(requestTo(SERVER + "/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"t\",\"expires_in\":60}",
                        MediaType.APPLICATION_JSON));

        // %40, not %2540. This is the whole point of the test.
        server.expect(requestTo(SERVER + "/admin/realms/" + REALM
                        + "/users?username=abbas.abbasli%40saipem.com&exact=true"))
                .andRespond(withSuccess("[{\"id\":\"kc-1\"}]", MediaType.APPLICATION_JSON));

        assertThat(service.findUserIdByUsername("abbas.abbasli@saipem.com"))
                .contains("kc-1");
        server.verify();
    }

    private static KeycloakAdminProperties props() {
        return new KeycloakAdminProperties(SERVER, REALM, "master", "admin-cli", "admin", "admin");
    }
}
