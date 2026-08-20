package az.millers.hcm.config.plan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The tiering itself — what each edition sells. */
class PlanTest {

    @Test
    @DisplayName("LITE is the SME set: people, org, hire-to-exit, time, leave, pay, approvals")
    void liteContents() {
        assertThat(Plan.LITE.modules()).containsExactlyInAnyOrder(
                HcmModule.SELF_SERVICE,
                HcmModule.PLATFORM_ADMIN,
                HcmModule.MANAGER_SELF_SERVICE,
                HcmModule.CORE_HR_EMPLOYEE_MANAGEMENT,
                HcmModule.CORE_HR_ORGANIZATION,
                HcmModule.EMPLOYEE_LIFECYCLE,
                HcmModule.TIME_ATTENDANCE,
                HcmModule.LEAVE_ABSENCE,
                HcmModule.PAYROLL,
                HcmModule.BENEFITS,
                HcmModule.WORKFLOW_APPROVALS,
                HcmModule.REPORTS_ANALYTICS);
    }

    @Test
    @DisplayName("payroll never ships without the allowance config it reads")
    void payrollImpliesBenefits() {
        // PayrollEngine resolves EmployeeAllowance / AllowanceType from the
        // compbenefits package, so a plan with payroll but no benefits could run
        // payroll yet never configure what it pays.
        for (Plan plan : Plan.values()) {
            if (plan.includes(HcmModule.PAYROLL)) {
                assertThat(plan.includes(HcmModule.BENEFITS))
                        .as("%s includes payroll, so it must include benefits", plan)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("tiers are strictly nested: LITE ⊂ STANDARD ⊂ ENTERPRISE")
    void tiersAreNested() {
        assertThat(Plan.STANDARD.modules()).containsAll(Plan.LITE.modules());
        assertThat(Plan.ENTERPRISE.modules()).containsAll(Plan.STANDARD.modules());
        assertThat(Plan.ENTERPRISE.modules()).containsExactlyInAnyOrder(HcmModule.values());
    }

    @Test
    @DisplayName("always-on modules are included by every plan, listed or not")
    void alwaysOnIncludedEverywhere() {
        for (Plan plan : Plan.values()) {
            for (HcmModule module : HcmModule.alwaysOnModules()) {
                assertThat(plan.includes(module))
                        .as("%s must include always-on %s", plan, module.key())
                        .isTrue();
            }
        }
        assertThat(HcmModule.alwaysOnModules())
                .containsExactlyInAnyOrder(HcmModule.SELF_SERVICE, HcmModule.PLATFORM_ADMIN);
    }

    @Test
    @DisplayName("the upsell target is the cheapest plan that unlocks the module")
    void lowestPlanWith() {
        assertThat(Plan.lowestPlanWith(HcmModule.LEAVE_ABSENCE)).isEqualTo(Plan.LITE);
        assertThat(Plan.lowestPlanWith(HcmModule.RECRUITMENT)).isEqualTo(Plan.STANDARD);
        assertThat(Plan.lowestPlanWith(HcmModule.COMPENSATION)).isEqualTo(Plan.ENTERPRISE);
        assertThat(Plan.lowestPlanWith(HcmModule.SELF_SERVICE)).isEqualTo(Plan.LITE);
    }

    @Test
    @DisplayName("persisted values parse leniently; junk falls back to the edition default")
    void parseIsLenient() {
        assertThat(Plan.parse("STANDARD")).isEqualTo(Plan.STANDARD);
        assertThat(Plan.parse(" enterprise ")).isEqualTo(Plan.ENTERPRISE);
        assertThat(Plan.parse(null)).isEqualTo(Plan.LITE);
        assertThat(Plan.parse("")).isEqualTo(Plan.LITE);
        assertThat(Plan.parse("PLATINUM")).isEqualTo(Plan.LITE);
    }

    @Test
    @DisplayName("every tier is unlimited until pricing lands")
    void limitsAreUnlimitedForNow() {
        for (Plan plan : Plan.values()) {
            assertThat(plan.limits().hasEmployeeLimit())
                    .as("%s employee limit", plan)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("module keys match the frontend category keys exactly")
    void moduleKeysAreStable() {
        // These strings are the wire format of disabled_modules and of the
        // frontend CATEGORIES list — renaming one silently un-disables a module.
        assertThat(HcmModule.byKey("leave-absence")).contains(HcmModule.LEAVE_ABSENCE);
        assertThat(HcmModule.byKey("core-hr-employee-management"))
                .contains(HcmModule.CORE_HR_EMPLOYEE_MANAGEMENT);
        assertThat(HcmModule.byKey("learning-lms")).contains(HcmModule.LEARNING_LMS);
        assertThat(HcmModule.byKey("nope")).isEmpty();
        assertThat(HcmModule.values()).hasSize(25);
    }
}
