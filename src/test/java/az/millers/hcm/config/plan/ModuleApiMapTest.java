package az.millers.hcm.config.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Path → owning module resolution behind {@link ModuleAccessFilter}. */
class ModuleApiMapTest {

    @Test
    @DisplayName("resolves a plain module root and everything under it")
    void resolvesRootAndDescendants() {
        assertThat(ModuleApiMap.resolve("/api/leave")).contains(HcmModule.LEAVE_ABSENCE);
        assertThat(ModuleApiMap.resolve("/api/leave/requests")).contains(HcmModule.LEAVE_ABSENCE);
        assertThat(ModuleApiMap.resolve("/api/leave/requests/42/approve"))
                .contains(HcmModule.LEAVE_ABSENCE);
    }

    @Test
    @DisplayName("longest prefix wins so nested ownership beats the parent")
    void longestPrefixWins() {
        // /api/reports belongs to reporting, but payroll reports are payroll's.
        assertThat(ModuleApiMap.resolve("/api/reports")).contains(HcmModule.REPORTS_ANALYTICS);
        assertThat(ModuleApiMap.resolve("/api/reports/definitions"))
                .contains(HcmModule.REPORTS_ANALYTICS);
        assertThat(ModuleApiMap.resolve("/api/reports/payroll")).contains(HcmModule.PAYROLL);
        assertThat(ModuleApiMap.resolve("/api/reports/payroll/summary")).contains(HcmModule.PAYROLL);
        assertThat(ModuleApiMap.resolve("/api/reports/recruitment"))
                .contains(HcmModule.RECRUITMENT);
    }

    @Test
    @DisplayName("prefixes match on segment boundaries, never mid-segment")
    void matchesOnSegmentBoundary() {
        // '/api/positions' must not swallow '/api/position-occupancies'.
        assertThat(ModuleApiMap.resolve("/api/positions"))
                .contains(HcmModule.CORE_HR_STAFFING_POSITIONS);
        assertThat(ModuleApiMap.resolve("/api/position-occupancies"))
                .contains(HcmModule.CORE_HR_STAFFING_POSITIONS);

        // A same-prefix root owned by nobody stays unowned rather than
        // inheriting a neighbour's module.
        assertThat(ModuleApiMap.resolve("/api/leaverboard")).isEmpty();
        assertThat(ModuleApiMap.resolve("/api/orgchart-export")).isEmpty();
    }

    @Test
    @DisplayName("shared / reference paths are owned by nobody and stay ungated")
    void sharedPathsAreUnowned() {
        assertThat(ModuleApiMap.resolve("/api/holidays")).isEmpty();
        assertThat(ModuleApiMap.resolve("/api/attachments/9")).isEmpty();
        assertThat(ModuleApiMap.resolve("/api/notifications")).isEmpty();
        assertThat(ModuleApiMap.resolve("/api/job-functions")).isEmpty();
    }

    @Test
    @DisplayName("trailing slashes and edge inputs resolve consistently")
    void handlesEdgeInputs() {
        assertThat(ModuleApiMap.resolve("/api/leave/")).contains(HcmModule.LEAVE_ABSENCE);
        assertThat(ModuleApiMap.resolve(null)).isEqualTo(Optional.empty());
        assertThat(ModuleApiMap.resolve("")).isEmpty();
        assertThat(ModuleApiMap.resolve("/")).isEmpty();
    }

    @Test
    @DisplayName("always-on modules own the self-service and admin surface")
    void alwaysOnSurface() {
        assertThat(ModuleApiMap.resolve("/api/self/policies")).contains(HcmModule.SELF_SERVICE);
        assertThat(ModuleApiMap.resolve("/api/me")).contains(HcmModule.SELF_SERVICE);
        assertThat(ModuleApiMap.resolve("/api/settings")).contains(HcmModule.PLATFORM_ADMIN);
        assertThat(ModuleApiMap.resolve("/api/module-settings")).contains(HcmModule.PLATFORM_ADMIN);
        assertThat(ModuleApiMap.resolve("/api/admin/tenants")).contains(HcmModule.PLATFORM_ADMIN);
    }
}
