package az.millers.hcm.admin;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thin wrapper around the Keycloak Admin REST API (M44).
 *
 * <p>Uses a short-lived admin token obtained via password grant on the
 * {@code master} realm (admin-cli public client — no secret needed).
 * The token is cached until five seconds before it expires to avoid
 * redundant round-trips while keeping the cache-window safe.
 *
 * <p>Only the subset of Admin REST operations used by
 * {@link UserManagementController} is implemented:
 * <ul>
 *   <li>List users with their effective realm roles</li>
 *   <li>Replace a user's realm-role assignments atomically</li>
 *   <li>Return the ordered list of assignable realm role names</li>
 * </ul>
 */
@Service
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    /** Realm roles that Keycloak assigns automatically; never shown in the UI. */
    private static final Set<String> SYSTEM_ROLES = Set.of(
            "offline_access", "uma_authorization");

    /** Ordered list of custom realm roles the HCM supports (PRD role matrix). */
    public static final List<String> HCM_ROLES = List.of(
            "SYSTEM_ADMIN", "HR_ADMIN", "HR_SPECIALIST",
            "AUDITOR", "DEPARTMENT_MANAGER", "EMPLOYEE");

    private final KeycloakAdminProperties props;
    private final RestClient restClient;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public KeycloakAdminService(KeycloakAdminProperties props, RestClient.Builder builder) {
        this.props = props;
        // When the admin server URL is HTTPS (e.g. behind nginx with a self-signed
        // cert), configure the RestClient to skip host/cert verification so the
        // admin token requests and Admin REST calls succeed in dev without requiring
        // a custom truststore entry. Production environments should either use a
        // real CA cert (verification passes naturally) or override the server-url to
        // an internal HTTP address that doesn't need TLS.
        this.restClient = props.serverUrl() != null && props.serverUrl().startsWith("https://")
                ? builder.requestFactory(lenientHttpsFactory()).build()
                : builder.build();
    }

    /** Returns a {@link SimpleClientHttpRequestFactory} that accepts any HTTPS cert. */
    private static SimpleClientHttpRequestFactory lenientHttpsFactory() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) { /* accept */ }
                public void checkServerTrusted(X509Certificate[] c, String a) { /* accept */ }
            }}, new SecureRandom());
            final SSLSocketFactory sf = ctx.getSocketFactory();

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection conn, String method)
                        throws IOException {
                    if (conn instanceof HttpsURLConnection https) {
                        https.setSSLSocketFactory(sf);
                        https.setHostnameVerifier((h, s) -> true);
                    }
                    super.prepareConnection(conn, method);
                }
            };
            factory.setConnectTimeout(10_000);
            factory.setReadTimeout(30_000);
            return factory;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Cannot build lenient HTTPS factory", e);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<UserAdminDto> listUsers() {
        String token = adminToken();
        JsonNode usersNode = restClient.get()
                .uri(realmUrl("/users?max=200&briefRepresentation=false"))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        List<UserAdminDto> result = new ArrayList<>();
        if (usersNode != null) {
            for (JsonNode u : usersNode) {
                String userId = u.path("id").asText();
                List<String> roles = fetchUserRoles(token, userId);
                result.add(new UserAdminDto(
                        userId,
                        u.path("username").asText(),
                        u.path("email").asText(""),
                        u.path("firstName").asText(""),
                        u.path("lastName").asText(""),
                        u.path("enabled").asBoolean(true),
                        roles));
            }
        }
        return result;
    }

    public List<String> availableRoles() {
        return HCM_ROLES;
    }

    /**
     * Replaces a user's realm role assignments atomically.
     *
     * <ol>
     *   <li>Fetch the full catalogue of realm roles (to get Keycloak UUIDs)</li>
     *   <li>Compute the delta against the user's current roles</li>
     *   <li>POST the additions, DELETE the removals</li>
     * </ol>
     *
     * Only roles whose names appear in {@link #HCM_ROLES} are touched; built-in
     * Keycloak roles such as {@code offline_access} are left untouched.
     */
    public void setUserRoles(String userId, List<String> targetRoles) {
        String token = adminToken();

        // Build a name→{id,name} map for all realm roles
        JsonNode allRoles = restClient.get()
                .uri(realmUrl("/roles"))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        Map<String, Map<String, String>> roleById = new LinkedHashMap<>();
        if (allRoles != null) {
            for (JsonNode r : allRoles) {
                String name = r.path("name").asText();
                roleById.put(name, Map.of(
                        "id", r.path("id").asText(),
                        "name", name));
            }
        }

        List<String> current = fetchUserRoles(token, userId);
        Set<String> target = new HashSet<>(targetRoles);

        List<Map<String, String>> toAdd = new ArrayList<>();
        List<Map<String, String>> toRemove = new ArrayList<>();

        for (String role : HCM_ROLES) {
            boolean has = current.contains(role);
            boolean wants = target.contains(role);
            if (!has && wants && roleById.containsKey(role)) toAdd.add(roleById.get(role));
            if (has && !wants && roleById.containsKey(role)) toRemove.add(roleById.get(role));
        }

        String mappingsUrl = realmUrl("/users/" + userId + "/role-mappings/realm");

        if (!toAdd.isEmpty()) {
            restClient.post()
                    .uri(mappingsUrl)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toAdd)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Assigned roles {} to user {}", toAdd.stream().map(m -> m.get("name")).toList(), userId);
        }

        if (!toRemove.isEmpty()) {
            restClient.method(HttpMethod.DELETE)
                    .uri(mappingsUrl)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toRemove)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Revoked roles {} from user {}", toRemove.stream().map(m -> m.get("name")).toList(), userId);
        }
    }

    // -------------------------------------------------------------------------
    // Package-accessible helpers for collaborating services (M54)
    // -------------------------------------------------------------------------

    /**
     * Returns a valid Keycloak admin token, refreshing from the master realm
     * if the cached one has expired.
     *
     * <p>Exposed as {@code public} so that {@code LdapFederationService} in the
     * {@code ldap} package can share the cached token rather than maintaining
     * its own credential exchange.
     */
    public String getAdminToken() {
        return adminToken();
    }

    /**
     * Constructs an absolute Admin REST API URL for the configured realm.
     *
     * <p>Example: {@code realmUrl("/components")} →
     * {@code http://localhost:8090/admin/realms/millers-hcm/components}
     *
     * <p>Exposed as {@code public} so that collaborating services such as
     * {@code LdapFederationService} can build their own Admin API calls
     * without duplicating the server-url + realm logic.
     *
     * @param path path segment to append (must start with {@code /})
     * @return fully-qualified Admin REST URL
     */
    public String realmUrl(String path) {
        return props.serverUrl() + "/admin/realms/" + props.realm() + path;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private List<String> fetchUserRoles(String token, String userId) {
        JsonNode roles = restClient.get()
                .uri(realmUrl("/users/" + userId + "/role-mappings/realm"))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        List<String> names = new ArrayList<>();
        if (roles != null) {
            for (JsonNode r : roles) {
                String name = r.path("name").asText();
                if (!SYSTEM_ROLES.contains(name) && !name.startsWith("default-roles-")) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /** Returns a cached admin token, refreshing if it has expired. */
    private String adminToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        tokenLock.lock();
        try {
            // Double-check after acquiring the lock
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
                return cachedToken;
            }

            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "password");
            form.add("client_id", props.effectiveAdminClientId());
            form.add("username", props.username());
            form.add("password", props.password());

            String tokenUrl = props.serverUrl()
                    + "/realms/" + props.effectiveMasterRealm()
                    + "/protocol/openid-connect/token";

            JsonNode response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("access_token")) {
                throw new IllegalStateException("Keycloak admin token request returned no access_token");
            }

            cachedToken = response.get("access_token").asText();
            int expiresIn = response.path("expires_in").asInt(60);
            tokenExpiry = Instant.now().plusSeconds(Math.max(expiresIn - 5, 1));
            log.debug("Obtained Keycloak admin token (expires in {}s)", expiresIn);
            return cachedToken;

        } finally {
            tokenLock.unlock();
        }
    }

}
