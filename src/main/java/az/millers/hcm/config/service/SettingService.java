package az.millers.hcm.config.service;
import az.millers.hcm.common.tenant.TenantContext;

import az.millers.hcm.config.domain.TenantSetting;
import az.millers.hcm.config.repo.TenantSettingRepository;
import az.millers.hcm.security.CurrentRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * M433 — Tenant setting service. manager_can_view_salary default false.
 */
@Service
public class SettingService {
    public static final String MANAGER_CAN_VIEW_SALARY = "manager_can_view_salary";
    /** CSV of module keys the tenant has switched off (hidden from the nav). */
    public static final String DISABLED_MODULES = "disabled_modules";

    private final TenantSettingRepository repo;
    private final CurrentRequest currentRequest;
    private final ApplicationEventPublisher events;

    public SettingService(TenantSettingRepository repo, CurrentRequest currentRequest,
                          ApplicationEventPublisher events) {
        this.repo = repo;
        this.currentRequest = currentRequest;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return getFor(TenantContext.current(), key, defaultValue);
    }

    /**
     * Read a setting for an explicit tenant rather than the request's.
     *
     * <p>Needed by callers that resolve config for a tenant other than the bound
     * one (module entitlement caches, admin tooling) — reading through
     * {@link TenantContext} there would silently answer for the wrong tenant.
     */
    @Transactional(readOnly = true)
    public String getFor(String tenantId, String key, String defaultValue) {
        return repo.findByTenantIdAndKey(tenantId, key)
                .map(TenantSetting::getValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        TenantSetting s = repo.findByTenantIdAndKey(TenantContext.current(), key).orElseGet(() -> {
            TenantSetting ns = new TenantSetting();
            ns.setTenantId(TenantContext.current());
            ns.setKey(key);
            ns.setCreatedBy(currentRequest.username());
            return ns;
        });
        s.setValue(value);
        s.setUpdatedBy(currentRequest.username());
        s.setUpdatedAt(OffsetDateTime.now());
        repo.save(s);
        // Caches derived from settings (module enablement) evict off this.
        events.publishEvent(new TenantSettingChangedEvent(TenantContext.current(), key));
    }

    public boolean managerCanViewSalary() {
        return "true".equalsIgnoreCase(get(MANAGER_CAN_VIEW_SALARY, "false"));
    }

    /** Module keys the current tenant has disabled (empty when all are on). */
    @Transactional(readOnly = true)
    public List<String> disabledModules() {
        return disabledModulesFor(TenantContext.current());
    }

    /** Module keys {@code tenantId} has disabled (empty when all are on). */
    @Transactional(readOnly = true)
    public List<String> disabledModulesFor(String tenantId) {
        String raw = getFor(tenantId, DISABLED_MODULES, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
