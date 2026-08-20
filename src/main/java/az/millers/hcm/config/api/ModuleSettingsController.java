package az.millers.hcm.config.api;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.config.plan.HcmModule;
import az.millers.hcm.config.plan.ModuleAccessService;
import az.millers.hcm.config.plan.Plan;
import az.millers.hcm.config.plan.PlanLimits;

/**
 * Per-tenant module enablement — the SPA's source of truth for what to render.
 *
 * <p>Readable by ANY authenticated user (unlike /api/settings which is HR_ADMIN
 * only) so navigation can hide modules for every user, not just admins. Writes
 * still go through the admin-only PUT /api/settings using the
 * {@code disabled_modules} key; the plan itself is control-plane
 * (SYSTEM_ADMIN, {@code /api/admin/tenants}).
 *
 * <p>The response separates the two reasons a module is off — outside the plan
 * (upsell) versus switched off by the tenant's own admin — because they need
 * different UI. {@code disabled} is retained as the union of both so an older
 * SPA build keeps working through a rollout.
 */
@RestController
@RequestMapping("/api/module-settings")
public class ModuleSettingsController {

    private final ModuleAccessService moduleAccess;

    public ModuleSettingsController(ModuleAccessService moduleAccess) {
        this.moduleAccess = moduleAccess;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ModuleSettingsDto get() {
        ModuleAccessService.EffectiveModules effective = moduleAccess.current();

        List<String> notInPlan = keys(effective.notInPlan());
        List<String> disabledByTenant = keys(effective.disabledByTenant());

        // Union — every module the SPA should not show, whatever the reason.
        List<String> disabled = java.util.stream.Stream
                .concat(notInPlan.stream(), disabledByTenant.stream())
                .sorted()
                .toList();

        return new ModuleSettingsDto(
                effective.plan().name(),
                disabled,
                keys(effective.enabled()),
                disabledByTenant,
                notInPlan,
                upgradeTargets(effective.notInPlan()),
                LimitsDto.of(effective.plan().limits()));
    }

    private static List<String> keys(Set<HcmModule> modules) {
        return modules.stream().map(HcmModule::key).sorted().toList();
    }

    /** For each out-of-plan module, the cheapest plan that unlocks it. */
    private static List<UpgradeTarget> upgradeTargets(Set<HcmModule> notInPlan) {
        return notInPlan.stream()
                .map(m -> new UpgradeTarget(m.key(), m.label(), Plan.lowestPlanWith(m).name()))
                .sorted(Comparator.comparing(UpgradeTarget::module))
                .toList();
    }

    /**
     * @param plan             the tenant's edition
     * @param disabled         union of notInPlan + disabledByTenant (back-compat)
     * @param enabled          modules usable right now
     * @param disabledByTenant in-plan but switched off by the tenant's admin
     * @param notInPlan        outside the plan — upgrade to unlock
     * @param upgrades         cheapest plan per out-of-plan module
     * @param limits           quantitative ceilings of the plan
     */
    public record ModuleSettingsDto(String plan,
                                    List<String> disabled,
                                    List<String> enabled,
                                    List<String> disabledByTenant,
                                    List<String> notInPlan,
                                    List<UpgradeTarget> upgrades,
                                    LimitsDto limits) {}

    public record UpgradeTarget(String module, String label, String requiredPlan) {}

    /** {@code null} means unlimited. */
    public record LimitsDto(Integer maxActiveEmployees) {
        static LimitsDto of(PlanLimits limits) {
            return new LimitsDto(limits.maxActiveEmployees());
        }
    }
}
