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
import org.springframework.security.oauth2.jwt.JwtDecoder;
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

        if (trustStorePath == null || trustStorePath.isBlank()) {
            log.info("Keycloak JwtDecoder: using JDK default trust (no custom truststore configured)");
            return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        }

        Path path = Path.of(trustStorePath);
        if (!Files.exists(path)) {
            log.warn("Keycloak truststore not found at {} — falling back to JDK default trust", path);
            return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
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

        RestTemplate rt = new RestTemplate(factory);
        log.info("Keycloak JwtDecoder: loaded {} truststore from {} ({} entries)",
                storeType, path, trustStore.size());

        return NimbusJwtDecoder.withIssuerLocation(issuerUri)
                .restOperations(rt)
                .build();
    }
}
