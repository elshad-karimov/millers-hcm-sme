package az.millers.hcm.config.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.common.tenant.TenantContext;
import az.millers.hcm.config.service.TenantSettingChangedEvent;

/** Plan entitlement × the tenant's own disabled_modules opt-out. */
class ModuleAccessServiceTest {

    private StubPlans plans;
    private StubSettings settings;
    private ModuleAccessService service;

    @BeforeEach
    void setUp() {
        plans = new StubPlans();
        settings = new StubSettings();
        service = new ModuleAccessService(plans, settings);
        TenantContext.set("acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void onPlan(Plan plan) {
        plans.uniform = plan;
    }

    private void disable(String... moduleKeys) {
        settings.disabled = List.of(moduleKeys);
    }

    @Test
    @DisplayName("a LITE tenant gets its plan modules and nothing else")
    void litePlanSplit() {
        onPlan(Plan.LITE);

        ModuleAccessService.EffectiveModules effective = service.current();

        assertThat(effective.plan()).isEqualTo(Plan.LITE);
        assertThat(effective.enabled()).contains(HcmModule.LEAVE_ABSENCE, HcmModule.PAYROLL);
        assertThat(effective.notInPlan()).contains(HcmModule.RECRUITMENT, HcmModule.COMPENSATION);
        assertThat(effective.disabledByTenant()).isEmpty();
        // Every module lands in exactly one bucket.
        assertThat(effective.enabled().size()
                + effective.notInPlan().size()
                + effective.disabledByTenant().size())
                .isEqualTo(HcmModule.values().length);
    }

    @Test
    @DisplayName("out-of-plan reads as an upsell, tenant-switched-off reads as their own choice")
    void reasonsAreDistinct() {
        onPlan(Plan.LITE);
        disable("time-attendance");

        assertThat(service.check(HcmModule.LEAVE_ABSENCE))
                .isEqualTo(ModuleAccessService.Reason.ALLOWED);
        assertThat(service.check(HcmModule.TIME_ATTENDANCE))
                .isEqualTo(ModuleAccessService.Reason.DISABLED_BY_TENANT);
        assertThat(service.check(HcmModule.RECRUITMENT))
                .isEqualTo(ModuleAccessService.Reason.NOT_IN_PLAN);
    }

    @Test
    @DisplayName("always-on modules survive both the plan and a hostile settings row")
    void alwaysOnCannotBeSwitchedOff() {
        onPlan(Plan.LITE);
        disable("self-service", "platform-admin", "leave-absence");

        assertThat(service.isEnabled(HcmModule.SELF_SERVICE)).isTrue();
        assertThat(service.isEnabled(HcmModule.PLATFORM_ADMIN)).isTrue();
        // ...while a normal module still honours the tenant's toggle.
        assertThat(service.isEnabled(HcmModule.LEAVE_ABSENCE)).isFalse();
    }

    @Test
    @DisplayName("a tenant cannot 'enable' a module its plan excludes by clearing the toggle")
    void planBeatsTenantToggle() {
        onPlan(Plan.LITE);
        disable();

        assertThat(service.isEnabled(HcmModule.RECRUITMENT)).isFalse();
        assertThat(service.check(HcmModule.RECRUITMENT))
                .isEqualTo(ModuleAccessService.Reason.NOT_IN_PLAN);
    }

    @Test
    @DisplayName("ENTERPRISE enables everything the tenant has not switched off")
    void enterpriseEnablesAll() {
        onPlan(Plan.ENTERPRISE);
        disable("engagement");

        ModuleAccessService.EffectiveModules effective = service.current();
        assertThat(effective.notInPlan()).isEmpty();
        assertThat(effective.disabledByTenant()).containsExactly(HcmModule.ENGAGEMENT);
        assertThat(effective.enabled()).hasSize(HcmModule.values().length - 1);
    }

    @Test
    @DisplayName("a settings write evicts that tenant's cached set, not everyone's")
    void settingsWriteEvictsOneTenant() {
        onPlan(Plan.LITE);
        assertThat(service.isEnabled(HcmModule.TIME_ATTENDANCE)).isTrue(); // caches 'acme'

        disable("time-attendance");
        // Stale until the event arrives — that is the cache doing its job.
        assertThat(service.isEnabled(HcmModule.TIME_ATTENDANCE)).isTrue();

        service.onSettingChanged(new TenantSettingChangedEvent("other", "disabled_modules"));
        assertThat(service.isEnabled(HcmModule.TIME_ATTENDANCE)).isTrue(); // wrong tenant

        service.onSettingChanged(new TenantSettingChangedEvent("acme", "disabled_modules"));
        assertThat(service.isEnabled(HcmModule.TIME_ATTENDANCE)).isFalse();
    }

    @Test
    @DisplayName("a plan change takes effect without a restart")
    void planChangeEvicts() {
        onPlan(Plan.LITE);
        assertThat(service.isEnabled(HcmModule.RECRUITMENT)).isFalse();

        onPlan(Plan.STANDARD);
        service.onPlanChanged(new TenantPlanChangedEvent("acme", Plan.LITE, Plan.STANDARD));

        assertThat(service.isEnabled(HcmModule.RECRUITMENT)).isTrue();
    }

    @Test
    @DisplayName("one tenant's plan never leaks into another's")
    void tenantsAreIsolated() {
        plans.perTenant.put("acme", Plan.LITE);
        plans.perTenant.put("globex", Plan.ENTERPRISE);

        assertThat(service.forTenant("acme").enabled()).doesNotContain(HcmModule.RECRUITMENT);
        assertThat(service.forTenant("globex").enabled()).contains(HcmModule.RECRUITMENT);
    }

    @Test
    @DisplayName("resolving another tenant reads THAT tenant's toggles, not the bound one's")
    void resolvesTogglesOfTheRequestedTenant() {
        // TenantContext is bound to 'acme' (see setUp). Reading the disabled list
        // through the ambient context would answer for the wrong tenant here.
        plans.uniform = Plan.ENTERPRISE;
        settings.perTenant.put("acme", List.of("engagement"));
        settings.perTenant.put("globex", List.of("health-safety"));

        assertThat(service.forTenant("globex").disabledByTenant())
                .containsExactly(HcmModule.HEALTH_SAFETY);
        assertThat(service.forTenant("acme").disabledByTenant())
                .containsExactly(HcmModule.ENGAGEMENT);
    }
}
