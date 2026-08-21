package az.millers.hcm.security.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Choosing where to fetch Keycloak's keys from.
 *
 * <p>The issuer is the public URL because that is the {@code iss} claim in
 * every token. Fetching keys over that same public URL sends each lookup out
 * through the reverse proxy and back, so a proxy blip or a Keycloak restart
 * breaks token validation for a backend sitting on the same network as
 * Keycloak — three 502s in production are what prompted this. Only the route
 * changes; what we accept as an issuer does not.
 */
class KeycloakJwtDecoderFactoryTest {

    private static String connectUri(String internalBase, String issuer) throws Exception {
        KeycloakJwtDecoderFactory f =
                new KeycloakJwtDecoderFactory("", "changeit", internalBase);
        Method m = KeycloakJwtDecoderFactory.class
                .getDeclaredMethod("internalConnectUri", String.class);
        m.setAccessible(true);
        return (String) m.invoke(f, issuer);
    }

    @Test
    @DisplayName("the realm path is carried over to the internal host")
    void graftsRealmPathOntoInternalBase() throws Exception {
        assertThat(connectUri("http://keycloak:8090",
                "https://hcm-sme.millers-software.com/realms/millers-hcm"))
                .isEqualTo("http://keycloak:8090/realms/millers-hcm");
    }

    @Test
    @DisplayName("each tenant's realm keeps its own path — one base, many realms")
    void isPerIssuerNotOneFixedUrl() throws Exception {
        assertThat(connectUri("http://keycloak:8090",
                "https://acme.example.com/realms/millers-acme"))
                .isEqualTo("http://keycloak:8090/realms/millers-acme");
    }

    @Test
    @DisplayName("unset means connect to the issuer, exactly as before")
    void blankBaseIsThePreviousBehaviour() throws Exception {
        String issuer = "https://hcm-sme.millers-software.com/realms/millers-hcm";
        assertThat(connectUri("", issuer)).isEqualTo(issuer);
        assertThat(connectUri(null, issuer)).isEqualTo(issuer);
        assertThat(connectUri("   ", issuer)).isEqualTo(issuer);
    }

    @Test
    @DisplayName("a trailing slash on the base does not produce a doubled one")
    void toleratesTrailingSlashes() throws Exception {
        assertThat(connectUri("http://keycloak:8090///",
                "https://public.example.com/realms/r"))
                .isEqualTo("http://keycloak:8090/realms/r");
    }

    @Test
    @DisplayName("an issuer with no path yields the bare internal base")
    void issuerWithoutPath() throws Exception {
        assertThat(connectUri("http://keycloak:8090", "https://public.example.com"))
                .isEqualTo("http://keycloak:8090");
    }
}
