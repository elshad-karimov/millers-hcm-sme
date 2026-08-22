package az.millers.hcm.admin;

import az.millers.hcm.common.UpstreamServiceException;
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
import org.springframework.web.client.RestClientResponseException;

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
        JsonNode usersNode;
        try {
            usersNode = restClient.get()
                    .uri(realmUrl("/users?max=200&briefRepresentation=false"))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            // Keycloak answers this endpoint from every user-storage provider in
            // the realm, and reports one broken provider as a flat 400
            // unknown_error over the whole query — the local users disappear
            // along with the federated ones. Left bare that surfaced as "500,
            // something went wrong", which points the operator at this
            // application when the fault is a federation provider next door.
            throw new UpstreamServiceException(
                    "Keycloak refused the user list (HTTP " + ex.getStatusCode().value()
                            + "). This is usually a user-federation provider in realm '"
                            + props.realm() + "' that Keycloak cannot reach — check the"
                            + " realm's user-storage providers and the Keycloak log.", ex);
        }

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

    /**
     * Creates a realm user and returns its id, or returns the id of the user
     * that already has this username.
     *
     * <p>No password is set, deliberately. The account is created with the
     * UPDATE_PASSWORD required action, so the person chooses their own the
     * first time they sign in and nobody — not HR, not this application, not a
     * log file — ever holds it. Until a password is established the account
     * exists and is linked but cannot authenticate, which is the safe state for
     * an account created in bulk from an HR screen.
     *
     * <p>Idempotent on username: re-running a hire, or hiring someone who
     * already had an account, links to the existing user instead of failing.
     */
    public String createUser(String username, String email, String firstName, String lastName,
                             String realmRole) {
        String token = adminToken();

        String existing = findUserIdByUsername(token, username);
        if (existing != null) {
            log.info("Keycloak user {} already exists — linking to it rather than creating", username);
            if (realmRole != null) addRealmRoleToUser(existing, realmRole);
            return existing;
        }

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("username", username);
        payload.put("enabled", true);
        if (email != null && !email.isBlank()) {
            payload.put("email", email);
            payload.put("emailVerified", false);
        }
        if (firstName != null) payload.put("firstName", firstName);
        if (lastName != null) payload.put("lastName", lastName);
        payload.put("requiredActions", List.of("UPDATE_PASSWORD"));

        try {
            restClient.post()
                    .uri(realmUrl("/users"))
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // 409 means somebody created it between the check and the write.
            if (ex.getStatusCode().value() != 409) {
                throw new UpstreamServiceException(
                        "Keycloak refused to create the login for " + username
                                + " (HTTP " + ex.getStatusCode().value() + ").", ex);
            }
        }

        String created = findUserIdByUsername(token, username);
        if (created == null) {
            throw new UpstreamServiceException(
                    "Keycloak accepted the login for " + username + " but it cannot be found again.");
        }
        if (realmRole != null) addRealmRoleToUser(created, realmRole);
        log.info("Created Keycloak user {} ({})", username, created);
        return created;
    }

    /**
     * Exact-username lookup; null when the realm has no such user.
     *
     * <p>The username goes in as a URI template variable rather than being
     * pre-encoded. RestClient encodes the template itself, so a pre-encoded
     * value is encoded twice — the {@code %40} of an email address becomes
     * {@code %2540} and Keycloak looks for a user literally called
     * "name%40company.com", finds nothing, and the caller concludes the
     * account it had just created did not exist.
     */
    private String findUserIdByUsername(String token, String username) {
        JsonNode found = restClient.get()
                .uri(realmUrl("/users?exact=true&username={username}"), username)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
        if (found != null && found.isArray() && !found.isEmpty()) {
            return found.get(0).path("id").asText(null);
        }
        return null;
    }

    public List<String> availableRoles() {
        return HCM_ROLES;
    }

    /**
     * Disables a Keycloak user by username (sets {@code enabled: false}).
     *
     * <p>Used by the termination access-revocation scheduler to block login
     * at EOD of the employee's effective termination date (PRD §8.11.6).
     *
     * <p>If the user is not found in Keycloak (e.g. external SSO-only account)
     * the method logs a warning and returns without error.
     *
     * @param username the Keycloak username to disable
     * @throws RuntimeException if the Keycloak Admin API call fails
     */
    public void disableUser(String username) {
        setUserEnabled(username, false);
    }

    /** Restores access — the other half of {@link #disableUser}, used on rehire. */
    public void enableUser(String username) {
        setUserEnabled(username, true);
    }

    /**
     * Turns a login on or off.
     *
     * <p>Disabling also ends the person's active sessions. Setting
     * {@code enabled=false} alone stops the NEXT sign-in; it leaves whoever is
     * already signed in holding a valid access token until it expires. For a
     * termination that is the wrong answer — the point is that they are out
     * now, not in a few minutes.
     */
    public void setUserEnabled(String username, boolean enabled) {
        String token = adminToken();

        // Template variable, not concatenation: an email username is the norm
        // here and the query has to survive its punctuation.
        JsonNode users = restClient.get()
                .uri(realmUrl("/users?username={username}&exact=true"), username)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        if (users == null || !users.isArray() || users.isEmpty()) {
            log.warn("setUserEnabled: Keycloak user '{}' not found — access unchanged", username);
            return;
        }

        String userId = users.get(0).path("id").asText();
        if (users.get(0).path("enabled").asBoolean(true) == enabled) {
            log.debug("setUserEnabled: Keycloak user '{}' is already {}", username,
                    enabled ? "enabled" : "disabled");
            return;
        }

        restClient.method(HttpMethod.PUT)
                .uri(realmUrl("/users/{userId}"), userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity();

        if (!enabled) {
            // Best effort: the account is already disabled, so a failure here
            // shortens nothing worse than the token's own lifetime.
            try {
                restClient.post()
                        .uri(realmUrl("/users/{userId}/logout"), userId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException ex) {
                log.warn("Disabled {} but could not end their sessions (HTTP {}) —"
                        + " existing tokens stay valid until they expire",
                        username, ex.getStatusCode().value());
            }
        }

        log.info("Keycloak account {} for user '{}'", enabled ? "enabled" : "disabled", username);
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
    // M265 / Phase F.6 — single-role grant/revoke + username lookup.
    //
    // Used by PositionProfileGrantService to wire the ACCESS_ROLE profile
    // item through to Keycloak realm-role grants on hire / revoke on
    // termination. The existing setUserRoles() is too coarse — it would
    // stomp on roles granted outside the profile flow.
    // -------------------------------------------------------------------------

    /**
     * Resolves a Keycloak user_id from a {@code preferred_username}. Returns
     * empty if the user doesn't exist (so the F.6 wire-up can soft-fail
     * for terminated / not-yet-provisioned employees).
     */
    public java.util.Optional<String> findUserIdByUsername(String username) {
        if (username == null || username.isBlank()) return java.util.Optional.empty();
        String token = adminToken();
        // Keycloak Admin: /users?username=<u>&exact=true → array of matches.
        JsonNode arr = restClient.get()
                // Template variable, not a pre-encoded string — see
                // findUserIdByUsername for what double-encoding costs.
                .uri(realmUrl("/users?username={username}&exact=true"), username)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
        if (arr == null || !arr.isArray() || arr.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(arr.get(0).path("id").asText());
    }

    /**
     * Grants a single realm role to a user (idempotent — Keycloak silently
     * no-ops if the role is already assigned).
     */
    /**
     * Gives a user a one-time password and returns it.
     *
     * <p>Every account this system creates starts with no password at all, so
     * somebody has to establish the first one. The proper way is a
     * password-setup email from Keycloak, but that needs SMTP on the realm and
     * this deployment has none configured — without this, the only way to start
     * an employee off is the Keycloak admin console, which is a different
     * application that HR should not need an account in.
     *
     * <p>The password is generated here, set with {@code temporary: true}, and
     * returned to the caller exactly once. Keycloak forces the holder to
     * replace it at first sign-in, so the value the administrator reads is
     * never the password the employee ends up with. It is deliberately never
     * logged, never stored, and never returned again — a second call generates
     * a different one.
     */
    public String resetToTemporaryPassword(String userId) {
        String password = generateTemporaryPassword();

        Map<String, Object> credential = new java.util.LinkedHashMap<>();
        credential.put("type", "password");
        credential.put("value", password);
        credential.put("temporary", true);

        try {
            restClient.method(HttpMethod.PUT)
                    .uri(realmUrl("/users/{userId}/reset-password"), userId)
                    .header("Authorization", "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(credential)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new UpstreamServiceException(
                    "Keycloak refused to set a temporary password (HTTP "
                            + ex.getStatusCode().value() + ").", ex);
        }
        // The user id, never the password.
        log.info("Set a temporary password for Keycloak user {}", userId);
        return password;
    }

    /**
     * A password that survives being read off a screen and typed by hand:
     * no characters that look like each other (0/O, 1/l/I), and one from each
     * class so it satisfies any realm password policy.
     */
    private static String generateTemporaryPassword() {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnopqrstuvwxyz";
        final String digits = "23456789";
        final String symbols = "!@#$%&*";
        final String all = upper + lower + digits + symbols;

        SecureRandom rnd = new SecureRandom();
        List<Character> chars = new java.util.ArrayList<>();
        chars.add(upper.charAt(rnd.nextInt(upper.length())));
        chars.add(lower.charAt(rnd.nextInt(lower.length())));
        chars.add(digits.charAt(rnd.nextInt(digits.length())));
        chars.add(symbols.charAt(rnd.nextInt(symbols.length())));
        while (chars.size() < 14) {
            chars.add(all.charAt(rnd.nextInt(all.length())));
        }
        // Shuffle, so the guaranteed classes are not always in the same slots.
        java.util.Collections.shuffle(chars, rnd);

        StringBuilder sb = new StringBuilder(chars.size());
        chars.forEach(sb::append);
        return sb.toString();
    }

    public void addRealmRoleToUser(String userId, String roleName) {
        Map<String, String> role = lookupRealmRole(roleName);
        if (role == null) {
            log.warn("Cannot grant role {} — not found in realm", roleName);
            return;
        }
        restClient.post()
                .uri(realmUrl("/users/" + userId + "/role-mappings/realm"))
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
        log.debug("Granted role {} to user {}", roleName, userId);
    }

    /**
     * Revokes a single realm role from a user. Idempotent — no-op if the
     * user doesn't currently hold the role.
     */
    public void removeRealmRoleFromUser(String userId, String roleName) {
        Map<String, String> role = lookupRealmRole(roleName);
        if (role == null) {
            log.warn("Cannot revoke role {} — not found in realm", roleName);
            return;
        }
        restClient.method(HttpMethod.DELETE)
                .uri(realmUrl("/users/" + userId + "/role-mappings/realm"))
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
        log.debug("Revoked role {} from user {}", roleName, userId);
    }

    /** Internal — find a realm role by name and return its {id,name} pair. */
    private Map<String, String> lookupRealmRole(String roleName) {
        if (roleName == null || roleName.isBlank()) return null;
        JsonNode r = restClient.get()
                .uri(realmUrl("/roles/{roleName}"), roleName)
                .header("Authorization", "Bearer " + adminToken())
                .retrieve()
                .body(JsonNode.class);
        if (r == null || r.path("id").isMissingNode()) return null;
        return Map.of("id", r.path("id").asText(), "name", r.path("name").asText());
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
