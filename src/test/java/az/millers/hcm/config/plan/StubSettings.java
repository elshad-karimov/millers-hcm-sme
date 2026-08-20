package az.millers.hcm.config.plan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import az.millers.hcm.config.service.SettingService;

/**
 * Hand-written stand-in for {@link SettingService}, exposing just the
 * {@code disabled_modules} answer the module gate reads.
 */
class StubSettings extends SettingService {

    /** Applies to any tenant unless {@link #perTenant} says otherwise. */
    List<String> disabled = List.of();

    final Map<String, List<String>> perTenant = new HashMap<>();

    StubSettings() {
        super(null, null, event -> { /* no listeners in unit tests */ });
    }

    @Override
    public List<String> disabledModulesFor(String tenantId) {
        return perTenant.getOrDefault(tenantId, disabled);
    }
}
