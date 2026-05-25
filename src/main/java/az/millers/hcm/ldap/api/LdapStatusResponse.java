package az.millers.hcm.ldap.api;

/**
 * Snapshot of the current Keycloak LDAP federation provider state.
 *
 * <p>Returned by {@code GET /api/admin/ldap/status}.
 *
 * @param setupStatus      {@code "configured"} if the LDAP provider exists in Keycloak,
 *                         {@code "not_configured"} otherwise.
 * @param providerId       Internal Keycloak UUID of the user-storage component,
 *                         or {@code null} when not configured.
 * @param providerName     Human-readable name of the user-storage component.
 * @param enabled          Whether the federation provider is active in Keycloak.
 * @param connectionUrl    LDAP server URL as stored in the provider config.
 * @param usersDn          Base DN used for user searches.
 * @param lastFullSync     Unix epoch seconds of the last full-sync run; {@code 0} = never.
 * @param lastChangedSync  Unix epoch seconds of the last changed-users sync; {@code 0} = never.
 * @param editMode         LDAP edit mode ({@code READ_ONLY}, {@code WRITABLE}, or
 *                         {@code UNSYNCED}).
 */
public record LdapStatusResponse(
        String setupStatus,
        String providerId,
        String providerName,
        boolean enabled,
        String connectionUrl,
        String usersDn,
        long lastFullSync,
        long lastChangedSync,
        String editMode
) {}
