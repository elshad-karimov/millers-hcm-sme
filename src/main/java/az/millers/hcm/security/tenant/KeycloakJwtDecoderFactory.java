package az.millers.hcm.security.tenant;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Builds a Keycloak-aware {@link JwtDecoder} for a <b>given</b> realm issuer
 * (multi-tenancy Phase 3).
 *
 * <p>This is the multi-issuer generalisation of the former single-issuer
 * {@code KeycloakJwtConfig}. It preserves both dev-mode quirks that made the
 * single-issuer decoder work:
 * <ul>
 *   <li><b>Custom truststore</b> — when {@code hcm.security.keycloak.trust-store-path}
 *       is set, JWKS fetches trust our self-signed Keycloak cert (used only for
 *       these fetches, not the whole JVM). Blank → JDK default trust.</li>
 *   <li><b>JWKS-URI rewrite</b> — we fetch the OIDC discovery doc, then rewrite the
 *       advertised {@code jwks_uri} to the same scheme/host/port as the configured
 *       issuer so Nimbus fetches keys through the reachable path (e.g. the Vite
 *       proxy on :5180) even when the realm advertises a different frontend URL.</li>
 * </ul>
 *
 * <p>The {@link az.millers.hcm.security.tenant.TenantAuthenticationResolver}
 * calls {@link #build(String)} once per trusted issuer and caches the result.
 */
@Component
public class KeycloakJwtDecoderFactory {

    private static final Logger log = LoggerFactory.getLogger(KeycloakJwtDecoderFactory.class);

    private final String trustStorePath;
    private final String trustStorePassword;

    public KeycloakJwtDecoderFactory(
            @Value("${hcm.security.keycloak.trust-store-path:}") String trustStorePath,
            @Value("${hcm.security.keycloak.trust-store-password:changeit}") String trustStorePassword) {
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
    }

    /** Build a validating decoder for the given realm issuer URI. */
    public JwtDecoder build(String issuerUri) {
        try {
            RestTemplate rt = buildRestTemplate();

            // Fetch discovery to get the real jwks_uri. withJwkSetUri (not
            // withIssuerLocation) so decoder construction tolerates a proxy that
            // changes the visible issuer host while we connect on another port.
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> oidcConfig = rt.getForObject(
                    issuerUri + "/.well-known/openid-configuration", java.util.Map.class);
            if (oidcConfig == null || !oidcConfig.containsKey("jwks_uri")) {
                throw new IllegalStateException(
                        "Could not fetch OIDC discovery document from " + issuerUri);
            }
            String discoveredJwksUri = (String) oidcConfig.get("jwks_uri");
            String realmIssuer = (String) oidcConfig.get("issuer");

            String jwksUri = rewriteHostPort(discoveredJwksUri, issuerUri);
            log.info("Keycloak decoder for issuer {}: jwks_uri={} advertised-iss={}",
                    issuerUri, jwksUri, realmIssuer);

            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                    .restOperations(rt)
                    .build();

            // Validate against the issuer the realm actually advertises.
            OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(realmIssuer);
            decoder.setJwtValidator(withIssuer);
            return decoder;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build JwtDecoder for issuer " + issuerUri, e);
        }
    }

    private RestTemplate buildRestTemplate() throws Exception {
        if (trustStorePath == null || trustStorePath.isBlank()) {
            return new RestTemplate();
        }
        Path path = Path.of(trustStorePath);
        if (!Files.exists(path)) {
            log.warn("Keycloak truststore not found at {} — falling back to JDK default trust", path);
            return new RestTemplate();
        }
        String storeType = trustStorePath.toLowerCase().endsWith(".jks") ? "JKS" : "PKCS12";
        KeyStore trustStore = KeyStore.getInstance(storeType);
        try (InputStream in = Files.newInputStream(path)) {
            trustStore.load(in, trustStorePassword.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                    throws IOException {
                if (connection instanceof HttpsURLConnection https) {
                    https.setSSLSocketFactory(sslContext.getSocketFactory());
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        log.info("Keycloak decoder: loaded {} truststore from {} ({} entries)",
                storeType, path, trustStore.size());
        return new RestTemplate(factory);
    }

    /** Replace the scheme+host+port of {@code target} with those of {@code issuerUri}. */
    private static String rewriteHostPort(String target, String issuerUri) {
        try {
            java.net.URI disco = java.net.URI.create(target);
            java.net.URI conn = java.net.URI.create(issuerUri);
            String connBase = conn.getScheme() + "://" + conn.getHost()
                    + (conn.getPort() > 0 ? ":" + conn.getPort() : "");
            String discoBase = disco.getScheme() + "://" + disco.getHost()
                    + (disco.getPort() > 0 ? ":" + disco.getPort() : "");
            return target.replace(discoBase, connBase);
        } catch (Exception ex) {
            return target;
        }
    }
}
