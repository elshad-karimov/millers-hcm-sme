package az.millers.hcm.config.service;

/**
 * Published whenever a tenant setting is written.
 *
 * <p>Lets caches derived from settings — notably the resolved module set behind
 * {@code /api/module-settings} and the enforcement filter — evict themselves
 * without {@code SettingService} depending on any of them.
 *
 * @param tenantId tenant whose settings changed
 * @param key      the setting key that was written
 */
public record TenantSettingChangedEvent(String tenantId, String key) {}
