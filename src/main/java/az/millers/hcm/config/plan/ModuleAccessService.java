package az.millers.hcm.config.plan;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import az.millers.hcm.common.tenant.TenantContext;
import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.config.service.TenantSettingChangedEvent;

/**
 * The effective module set for a tenant: what its {@link Plan} entitles it to,
 * minus what its own admin switched off in {@code disabled_modules}.
 *
 * <p>Two layers, kept distinct because they mean different things to a user:
 * <ul>
 *   <li>{@link Reason#NOT_IN_PLAN} — sell them an upgrade.</li>
 *   <li>{@link Reason#DISABLED_BY_TENANT} — their own admin's choice, and their
 *       own admin can undo it.</li>
 * </ul>
 *
 * <p>{@link HcmModule#alwaysOn()} modules ignore both layers, so no plan and no
 * botched settings write can lock a tenant out of its own self-service or the
 * admin screen that re-enables everything.
 *
 * <p>Resolved sets are cached per tenant and evicted on a settings write
 * ({@link TenantSettingChangedEvent}) or a plan change.
 */
@Service
public class ModuleAccessService {

    /** Why a module is unavailable. */
    public enum Reason {
        ALLOWED,
        /** The tenant's plan does not include it — upsell. */
        NOT_IN_PLAN,
        /** In the plan, but the tenant's own admin switched it off. */
        DISABLED_BY_TENANT
    }

    /**
     * A tenant's resolved module picture.
     *
     * @param plan             the tenant's plan
     * @param enabled          usable right now
     * @param disabledByTenant in the plan, switched off by the tenant
     * @param notInPlan        outside the plan — upgrade to get them
     */
    public record EffectiveModules(Plan plan,
                                   Set<HcmModule> enabled,
                                   Set<HcmModule> disabledByTenant,
                                   Set<HcmModule> notInPlan) {}

    private final TenantPlanService plans;
    private final SettingService settings;
    private final Map<String, EffectiveModules> cache = new ConcurrentHashMap<>();

    public ModuleAccessService(TenantPlanService plans, SettingService settings) {
        this.plans = plans;
        this.settings = settings;
    }

    /** Resolved modules for the tenant bound to the current request. */
    public EffectiveModules current() {
        return forTenant(TenantContext.current());
    }

    public EffectiveModules forTenant(String tenantId) {
        return cache.computeIfAbsent(tenantId, this::resolve);
    }

    private EffectiveModules resolve(String tenantId) {
        Plan plan = plans.planFor(tenantId);

        // Explicit tenant, not TenantContext: forTenant() may resolve a tenant
        // other than the one bound to the request.
        Set<String> disabledKeys = new HashSet<>(settings.disabledModulesFor(tenantId));

        Set<HcmModule> enabled = EnumSet.noneOf(HcmModule.class);
        Set<HcmModule> disabledByTenant = EnumSet.noneOf(HcmModule.class);
        Set<HcmModule> notInPlan = EnumSet.noneOf(HcmModule.class);

        for (HcmModule module : HcmModule.values()) {
            if (module.alwaysOn()) {
                enabled.add(module);
            } else if (!plan.includes(module)) {
                notInPlan.add(module);
            } else if (disabledKeys.contains(module.key())) {
                disabledByTenant.add(module);
            } else {
                enabled.add(module);
            }
        }
        return new EffectiveModules(plan, Set.copyOf(enabled),
                Set.copyOf(disabledByTenant), Set.copyOf(notInPlan));
    }

    /** Why {@code module} is (un)available to the current tenant. */
    public Reason check(HcmModule module) {
        EffectiveModules effective = current();
        if (effective.enabled().contains(module)) {
            return Reason.ALLOWED;
        }
        return effective.notInPlan().contains(module)
                ? Reason.NOT_IN_PLAN
                : Reason.DISABLED_BY_TENANT;
    }

    public boolean isEnabled(HcmModule module) {
        return current().enabled().contains(module);
    }

    /**
     * A settings write may have changed {@code disabled_modules} — drop that
     * tenant's resolved set. Cheap to rebuild; correctness beats a hit rate.
     */
    @EventListener
    public void onSettingChanged(TenantSettingChangedEvent event) {
        cache.remove(event.tenantId());
    }

    /** A plan move changes the entitlement itself — drop the resolved set. */
    @EventListener
    public void onPlanChanged(TenantPlanChangedEvent event) {
        cache.remove(event.tenantId());
    }

    public void evict(String tenantId) {
        cache.remove(tenantId);
    }

    public void evictAll() {
        cache.clear();
    }
}
