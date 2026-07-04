package az.millers.hcm.ldap;

import az.millers.hcm.admin.KeycloakAdminService;
import az.millers.hcm.ldap.api.LdapStatusResponse;
import az.millers.hcm.ldap.api.LdapSyncResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.*;

/**
 * Manages the Keycloak LDAP user-storage federation provider for the
 * Millers HCM platform (M54 — PRD §14.6).
 *
 * <p>Keycloak's Admin REST API is used directly via {@link RestClient}.
 * Admin tokens are obtained (and cached) by delegating to
 * {@link KeycloakAdminService#getAdminToken()}.
 *
 * <h3>Keycloak component model</h3>
 * <ul>
 *   <li>The LDAP user-storage provider is a {@code component} of type
 *       {@code org.keycloak.storage.UserStorageProvider}.</li>
 *   <li>Attribute/role mappers are child {@code components} whose
 *       {@code parentId} is the user-storage component UUID.</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * {@link #setupIfNeeded()} is safe to call multiple times. It queries the
 * existing components list before attempting creation and skips the create
 * step if a provider named {@link LdapProperties#providerName()} already
 * exists. Individual mappers are likewise skipped if already present.
 *
 * <h3>Error tolerance</h3>
 * All Keycloak calls are wrapped in try/catch. If Keycloak is unreachable
 * (e.g. during a rolling deploy or first-boot race), the method logs a
 * warning and returns gracefully. This prevents startup failures when the
 * LDAP or Keycloak container is still initialising.
 */
@Service
@EnableConfigurationProperties(LdapProperties.class)
public class LdapFederationService {

    private static final Logger log = LoggerFactory.getLogger(LdapFederationService.class);

    private static final String USER_STORAGE_PROVIDER_TYPE =
            "org.keycloak.storage.UserStorageProvider";
    private static final String LDAP_STORAGE_MAPPER_TYPE =
            "org.keycloak.storage.ldap.mappers.LDAPStorageMapper";

    private final LdapProperties props;
    private final KeycloakAdminService keycloakAdminService;
    private final RestClient restClient;

    public LdapFederationService(LdapProperties props,
                                  KeycloakAdminService keycloakAdminService,
                                  RestClient.Builder builder) {
        this.props = props;
        this.keycloakAdminService = keycloakAdminService;
        this.restClient = builder.build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Idempotently creates the LDAP federation provider in Keycloak if it does
     * not already exist, then creates the attribute / role mappers and triggers
     * an initial full synchronisation.
     *
     * <p>Safe to call multiple times; each step is guarded by an existence
     * check. All exceptions are swallowed and logged as warnings so that
     * application startup is never blocked by a temporarily unavailable
     * Keycloak or OpenLDAP instance.
     */
    public void setupIfNeeded() {
        try {
            String token = keycloakAdminService.getAdminToken();
            String realmId = fetchRealmId(token);

            Optional<String> existingId = findProvider(token, realmId);
            String providerId;
            if (existingId.isPresent()) {
                providerId = existingId.get();
                log.info("LDAP federation provider '{}' already exists (id={}); skipping creation",
                        props.providerName(), providerId);
            } else {
                providerId = createProvider(token, realmId);
                log.info("Created LDAP federation provider '{}' (id={})",
                        props.providerName(), providerId);
            }

            ensureMappers(token, realmId, providerId);

            // Trigger initial sync to populate Keycloak with LDAP users
            log.info("Triggering initial LDAP full sync...");
            LdapSyncResult result = doSync(token, providerId, "triggerFullSync");
            log.info("Initial LDAP sync complete: added={}, updated={}, removed={}, failed={}",
                    result.added(), result.updated(), result.removed(), result.failed());

        } catch (RestClientException ex) {
            log.warn("LDAP setup failed (Keycloak/LDAP may not be ready yet): {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("LDAP setup encountered an unexpected error: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Returns the current status of the Keycloak LDAP federation provider.
     *
     * <p>If Keycloak is unreachable or the provider does not exist, returns a
     * {@link LdapStatusResponse} with {@code setupStatus = "not_configured"}.
     */
    public LdapStatusResponse getStatus() {
        try {
            String token = keycloakAdminService.getAdminToken();
            String realmId = fetchRealmId(token);
            Optional<String> providerId = findProvider(token, realmId);

            if (providerId.isEmpty()) {
                return new LdapStatusResponse("not_configured", null,
                        props.providerName(), false, props.connectionUrl(),
                        props.usersDn(), 0, 0, "READ_ONLY");
            }

            JsonNode component = fetchComponent(token, realmId, providerId.get());
            JsonNode config = component.path("config");
            boolean enabled = firstConfigValue(config, "enabled", "true").equalsIgnoreCase("true");
            String connectionUrl = firstConfigValue(config, "connectionUrl", props.connectionUrl());
            String usersDn = firstConfigValue(config, "usersDn", props.usersDn());
            String editMode = firstConfigValue(config, "editMode", "READ_ONLY");
            long lastFullSync = parseLongConfigValue(config, "lastSync");
            long lastChangedSync = parseLongConfigValue(config, "changedSyncPeriod");

            return new LdapStatusResponse(
                    "configured",
                    providerId.get(),
                    component.path("name").asText(props.providerName()),
                    enabled,
                    connectionUrl,
                    usersDn,
                    lastFullSync,
                    lastChangedSync,
                    editMode
            );

        } catch (Exception ex) {
            log.warn("Could not fetch LDAP provider status: {}", ex.getMessage());
            return new LdapStatusResponse("not_configured", null,
                    props.providerName(), false, props.connectionUrl(),
                    props.usersDn(), 0, 0, "READ_ONLY");
        }
    }

    /**
     * Triggers a full synchronisation of all LDAP users into Keycloak.
     *
     * @return sync result counters from Keycloak
     * @throws IllegalStateException if the LDAP provider is not configured
     */
    public LdapSyncResult syncFull() {
        return sync("triggerFullSync");
    }

    /**
     * Triggers a changed-users synchronisation (only users modified since the
     * last sync are imported / updated).
     *
     * @return sync result counters from Keycloak
     * @throws IllegalStateException if the LDAP provider is not configured
     */
    public LdapSyncResult syncChanged() {
        return sync("triggerChangedUsersSync");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private LdapSyncResult sync(String action) {
        try {
            String token = keycloakAdminService.getAdminToken();
            String realmId = fetchRealmId(token);
            Optional<String> providerId = findProvider(token, realmId);
            if (providerId.isEmpty()) {
                throw new IllegalStateException(
                        "LDAP provider '" + props.providerName() + "' is not configured in Keycloak. " +
                        "Call POST /api/admin/ldap/setup first.");
            }
            return doSync(token, providerId.get(), action);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("LDAP sync '{}' failed: {}", action, ex.getMessage());
            return new LdapSyncResult(action, 0, 0, 0, 0);
        }
    }

    /**
     * Executes a Keycloak sync action and parses the result counters.
     * POST /admin/realms/{realm}/user-storage/{id}/sync?action={action}
     */
    private LdapSyncResult doSync(String token, String providerId, String action) {
        JsonNode result = restClient.post()
                .uri(keycloakAdminService.realmUrl(
                        "/user-storage/" + providerId + "/sync?action=" + action))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        if (result == null) {
            return new LdapSyncResult(action, 0, 0, 0, 0);
        }
        return new LdapSyncResult(
                action,
                result.path("added").asInt(0),
                result.path("updated").asInt(0),
                result.path("removed").asInt(0),
                result.path("failed").asInt(0)
        );
    }

    /**
     * Fetches the Keycloak internal realm UUID (needed as {@code parentId}
     * when creating components).
     * GET /admin/realms/{realm} → field "id"
     */
    private String fetchRealmId(String token) {
        JsonNode realm = restClient.get()
                .uri(keycloakAdminService.realmUrl(""))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        if (realm == null || !realm.has("id")) {
            throw new IllegalStateException("Could not retrieve realm id from Keycloak");
        }
        return realm.path("id").asText();
    }

    /**
     * Searches for an existing user-storage component named
     * {@link LdapProperties#providerName()}.
     * GET /admin/realms/{realm}/components?type=org.keycloak.storage.UserStorageProvider
     */
    private Optional<String> findProvider(String token, String realmId) {
        JsonNode components = restClient.get()
                .uri(keycloakAdminService.realmUrl(
                        "/components?type=" + USER_STORAGE_PROVIDER_TYPE))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        if (components == null || !components.isArray()) {
            return Optional.empty();
        }
        for (JsonNode comp : components) {
            if (props.providerName().equals(comp.path("name").asText())) {
                return Optional.of(comp.path("id").asText());
            }
        }
        return Optional.empty();
    }

    /**
     * Fetches a single component by id.
     * GET /admin/realms/{realm}/components/{id}
     */
    private JsonNode fetchComponent(String token, String realmId, String componentId) {
        return restClient.get()
                .uri(keycloakAdminService.realmUrl("/components/" + componentId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
    }

    /**
     * Creates the LDAP user-storage provider component in Keycloak.
     * POST /admin/realms/{realm}/components → 201 Location: .../components/{id}
     *
     * @return the newly created component UUID
     */
    private String createProvider(String token, String realmId) {
        Map<String, Object> payload = Map.of(
                "name", props.providerName(),
                "providerId", "ldap",
                "providerType", USER_STORAGE_PROVIDER_TYPE,
                "parentId", realmId,
                "config", Map.ofEntries(
                        Map.entry("connectionUrl", List.of(props.connectionUrl())),
                        Map.entry("bindDn", List.of(props.bindDn())),
                        Map.entry("bindCredential", List.of(props.bindCredential())),
                        Map.entry("usersDn", List.of(props.usersDn())),
                        Map.entry("usernameLDAPAttribute", List.of("cn")),
                        Map.entry("rdnLDAPAttribute", List.of("cn")),
                        Map.entry("uuidLDAPAttribute", List.of("entryUUID")),
                        Map.entry("userObjectClasses", List.of("inetOrgPerson")),
                        Map.entry("editMode", List.of("READ_ONLY")),
                        Map.entry("importEnabled", List.of("true")),
                        Map.entry("syncRegistrations", List.of("false")),
                        Map.entry("enabled", List.of("true")),
                        Map.entry("priority", List.of("0")),
                        Map.entry("fullSyncPeriod", List.of("3600")),
                        Map.entry("changedSyncPeriod", List.of("-1")),
                        Map.entry("connectionPooling", List.of("true")),
                        Map.entry("vendor", List.of("other")),
                        Map.entry("pagination", List.of("true"))
                )
        );

        ResponseEntity<Void> response = restClient.post()
                .uri(keycloakAdminService.realmUrl("/components"))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        return extractIdFromLocation(response);
    }

    /**
     * Ensures the standard attribute and role mappers exist under the provider.
     * Each mapper creation is skipped if a mapper with the same name already exists.
     */
    private void ensureMappers(String token, String realmId, String providerId) {
        // Load existing mappers to support idempotency
        Set<String> existingMapperNames = fetchExistingMapperNames(token, realmId, providerId);

        if (!existingMapperNames.contains("username")) {
            createMapper(token, providerId, buildUsernameMapper(providerId));
            log.debug("Created LDAP username mapper");
        }
        if (!existingMapperNames.contains("email")) {
            createMapper(token, providerId, buildEmailMapper(providerId));
            log.debug("Created LDAP email mapper");
        }
        if (!existingMapperNames.contains("ldap-roles")) {
            createMapper(token, providerId, buildRoleMapper(providerId));
            log.debug("Created LDAP role mapper");
        }
    }

    /**
     * Returns the set of mapper names already registered under the given provider.
     * GET /admin/realms/{realm}/components?parent={providerId}&type=org.keycloak.storage.ldap.mappers.LDAPStorageMapper
     */
    private Set<String> fetchExistingMapperNames(String token, String realmId, String providerId) {
        try {
            JsonNode mappers = restClient.get()
                    .uri(keycloakAdminService.realmUrl(
                            "/components?parent=" + providerId + "&type=" + LDAP_STORAGE_MAPPER_TYPE))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);

            Set<String> names = new HashSet<>();
            if (mappers != null && mappers.isArray()) {
                for (JsonNode m : mappers) {
                    names.add(m.path("name").asText());
                }
            }
            return names;
        } catch (Exception ex) {
            log.warn("Could not fetch existing LDAP mappers: {}", ex.getMessage());
            return Set.of();
        }
    }

    /**
     * Creates a single LDAP storage mapper sub-component under the provider.
     */
    private void createMapper(String token, String providerId, Map<String, Object> mapperPayload) {
        restClient.post()
                .uri(keycloakAdminService.realmUrl("/components"))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapperPayload)
                .retrieve()
                .toBodilessEntity();
    }

    /** Builds the username attribute mapper payload. */
    private Map<String, Object> buildUsernameMapper(String providerId) {
        return Map.of(
                "name", "username",
                "providerId", "user-attribute-ldap-mapper",
                "providerType", LDAP_STORAGE_MAPPER_TYPE,
                "parentId", providerId,
                "config", Map.of(
                        "ldap.attribute", List.of("cn"),
                        "user.model.attribute", List.of("username"),
                        "read.only", List.of("true"),
                        "always.read.value.from.ldap", List.of("false"),
                        "is.mandatory.in.ldap", List.of("true")
                )
        );
    }

    /** Builds the email attribute mapper payload. */
    private Map<String, Object> buildEmailMapper(String providerId) {
        return Map.of(
                "name", "email",
                "providerId", "user-attribute-ldap-mapper",
                "providerType", LDAP_STORAGE_MAPPER_TYPE,
                "parentId", providerId,
                "config", Map.of(
                        "ldap.attribute", List.of("mail"),
                        "user.model.attribute", List.of("email"),
                        "read.only", List.of("true"),
                        "always.read.value.from.ldap", List.of("false"),
                        "is.mandatory.in.ldap", List.of("false")
                )
        );
    }

    /**
     * Builds the role-ldap-mapper payload.
     * Maps LDAP groups in {@link LdapProperties#groupsDn()} to Keycloak realm roles.
     * Group {@code cn} values must match realm role names exactly.
     */
    private Map<String, Object> buildRoleMapper(String providerId) {
        return Map.of(
                "name", "ldap-roles",
                "providerId", "role-ldap-mapper",
                "providerType", LDAP_STORAGE_MAPPER_TYPE,
                "parentId", providerId,
                "config", Map.of(
                        "roles.dn", List.of(props.groupsDn()),
                        "role.name.ldap.attribute", List.of("cn"),
                        "role.object.classes", List.of("groupOfNames"),
                        "membership.ldap.attribute", List.of("member"),
                        "membership.attribute.type", List.of("DN"),
                        "roles.ldap.filter", List.of(""),
                        "mode", List.of("READ_ONLY"),
                        "user.roles.retrieve.strategy", List.of("LOAD_ROLES_BY_MEMBER_ATTRIBUTE"),
                        "use.realm.roles.mapping", List.of("true"),
                        "client.id", List.of("")
                )
        );
    }

    /**
     * Extracts the component UUID from the {@code Location} header returned by
     * a 201 Created response.
     * Expected format: {@code .../components/{uuid}}
     */
    private String extractIdFromLocation(ResponseEntity<Void> response) {
        var location = response.getHeaders().getLocation();
        if (location == null) {
            throw new IllegalStateException(
                    "Keycloak component creation returned 201 but no Location header");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Returns the first value of a multi-valued config entry, or the fallback. */
    private String firstConfigValue(JsonNode config, String key, String fallback) {
        JsonNode arr = config.path(key);
        if (arr.isArray() && arr.size() > 0) {
            return arr.get(0).asText(fallback);
        }
        return fallback;
    }

    /**
     * Parses a long value from a multi-valued config entry.
     * Returns {@code 0} if the key is absent or the value is not numeric.
     */
    private long parseLongConfigValue(JsonNode config, String key) {
        String raw = firstConfigValue(config, key, "0");
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
