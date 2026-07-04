package az.millers.hcm.config;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Custom {@link JwtDecoder} that trusts our self-signed Keycloak cert
 * via a small dedicated truststore (PRD 14.6 hardening).
 *
 * <p>The default Spring Boot OAuth2 resource-server autoconfig builds
 * the decoder with a plain {@link RestTemplate} whose
 * {@link SimpleClientHttpRequestFactory} uses the JDK's default
 * {@code cacerts}. That truststore doesn't include our self-signed
 * cert, so the JWKS fetch (and every periodic refresh) fails with
 * "PKIX path building failed" once we move Keycloak behind HTTPS.
 *
 * <p>This bean wires a decoder whose HTTPS calls go through a
 * {@link SimpleClientHttpRequestFactory} subclass that pins our
 * truststore on outbound {@link HttpsURLConnection}s. Crucially, the
 * truststore is <b>used only for JWKS fetches</b> — the rest of the
 * JVM's HTTPS calls (MinIO, MailHog, future external services) keep
 * using the default JDK trust.
 *
 * <p>When {@code hcm.security.keycloak.trust-store-path} is blank the
 * decoder falls back to the Spring Boot default, preserving back-compat
 * for any environment that doesn't need the custom truststore (e.g.,
 * production behind a real CA).
 */
@Configuration
public class KeycloakJwtConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycloakJwtConfig.class);

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${hcm.security.keycloak.trust-store-path:}") String trustStorePath,
            @Value("${hcm.security.keycloak.trust-store-password:changeit}") String trustStorePassword)
            throws Exception {

        // ── Build the RestTemplate (with optional custom truststore) ──────────
        RestTemplate rt;
        if (trustStorePath == null || trustStorePath.isBlank()) {
            log.info("Keycloak JwtDecoder: using JDK default trust (no custom truststore configured)");
            rt = new RestTemplate();
        } else {
            Path path = Path.of(trustStorePath);
            if (!Files.exists(path)) {
                log.warn("Keycloak truststore not found at {} — falling back to JDK default trust", path);
                rt = new RestTemplate();
            } else {
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
                rt = new RestTemplate(factory);
                log.info("Keycloak JwtDecoder: loaded {} truststore from {} ({} entries)",
                        storeType, path, trustStore.size());
            }
        }

        // ── Fetch the OIDC discovery document to get the real jwks_uri ────────
        // We use withJwkSetUri() instead of withIssuerLocation() so that the
        // decoder construction does NOT require the iss in the discovery doc to
        // match issuerUri exactly.  This tolerates the common dev-mode setup
        // where a Vite/nginx proxy changes the visible issuer hostname while the
        // backend connects to Keycloak directly on a different port.
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> oidcConfig = rt.getForObject(
                issuerUri + "/.well-known/openid-configuration",
                java.util.Map.class);
        if (oidcConfig == null || !oidcConfig.containsKey("jwks_uri")) {
            throw new IllegalStateException(
                    "Could not fetch OIDC discovery document from " + issuerUri);
        }
        String discoveredJwksUri = (String) oidcConfig.get("jwks_uri");
        String realmIssuer       = (String) oidcConfig.get("issuer"); // may differ from issuerUri

        // Rewrite the JWKS URI to use the same host:port as issuerUri so that
        // Nimbus fetches keys directly from Keycloak even when the realm
        // advertises a different frontend URL (e.g. a Vite proxy on :5180).
        // Pattern: replace the scheme+host+port prefix of discoveredJwksUri
        // with the scheme+host+port of issuerUri.
        String jwksUri;
        try {
            java.net.URI disco = java.net.URI.create(discoveredJwksUri);
            java.net.URI conn  = java.net.URI.create(issuerUri);
            String connBase = conn.getScheme() + "://" + conn.getHost()
                    + (conn.getPort() > 0 ? ":" + conn.getPort() : "");
            String discoBase = disco.getScheme() + "://" + disco.getHost()
                    + (disco.getPort() > 0 ? ":" + disco.getPort() : "");
            jwksUri = discoveredJwksUri.replace(discoBase, connBase);
        } catch (Exception ex) {
            jwksUri = discoveredJwksUri; // fall back to as-discovered
        }
        log.info("Keycloak OIDC discovery: jwks_uri={} iss={}", jwksUri, realmIssuer);

        // Build decoder against the real JWKS endpoint.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .restOperations(rt)
                .build();

        // Add issuer validator using the issuer the realm actually advertises.
        OAuth2TokenValidator<Jwt> issuerValidator =
                new JwtClaimValidator<>(JwtClaimNames.ISS,
                        iss -> realmIssuer.equals(iss));
        OAuth2TokenValidator<Jwt> withIssuer =
                org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(realmIssuer);
        decoder.setJwtValidator(withIssuer);

        return decoder;
    }
}
