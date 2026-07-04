package az.millers.hcm.ldap.api;

/**
 * Result of a Keycloak LDAP synchronisation operation.
 *
 * <p>Keycloak's {@code /sync} endpoint returns a JSON body with these counters;
 * this record is a typed projection of that response.
 *
 * @param action   The sync action that was performed ({@code "triggerFullSync"}
 *                 or {@code "triggerChangedUsersSync"}).
 * @param added    Number of user entries added to Keycloak from LDAP.
 * @param updated  Number of existing Keycloak users whose attributes were refreshed.
 * @param removed  Number of Keycloak users removed because they no longer exist in LDAP.
 * @param failed   Number of entries that could not be imported due to mapping errors.
 */
public record LdapSyncResult(
        String action,
        int added,
        int updated,
        int removed,
        int failed
) {}
