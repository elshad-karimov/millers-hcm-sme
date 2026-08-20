package az.millers.hcm.config.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.common.tenant.TenantContext;

/**
 * The gate itself: does a module outside the plan actually stop answering?
 *
 * <p>This is the difference between a plan and a cosmetic nav filter — without
 * it, {@code curl /api/recruitment/vacancies} still returns data for a LITE
 * tenant.
 */
class ModuleAccessFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StubSettings settings;
    private StubPlans plans;
    private ModuleAccessFilter filter;

    @BeforeEach
    void setUp() {
        settings = new StubSettings();
        plans = new StubPlans();
        filter = new ModuleAccessFilter(new ModuleAccessService(plans, settings), MAPPER);
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

    /** Runs the filter and reports whether the request reached the chain. */
    private Outcome call(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(chain.getRequest() != null, response);
    }

    private record Outcome(boolean passedThrough, MockHttpServletResponse response) {}

    @SuppressWarnings("unchecked")
    private Map<String, Object> bodyOf(MockHttpServletResponse response) throws Exception {
        return MAPPER.readValue(response.getContentAsByteArray(), Map.class);
    }

    @Test
    @DisplayName("LITE tenant: an in-plan module answers normally")
    void inPlanPassesThrough() throws Exception {
        onPlan(Plan.LITE);

        Outcome outcome = call("GET", "/api/leave/requests");

        assertThat(outcome.passedThrough()).isTrue();
        assertThat(outcome.response().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("LITE tenant: an out-of-plan module is 403, not merely hidden")
    void outOfPlanIsForbidden() throws Exception {
        onPlan(Plan.LITE);

        Outcome outcome = call("GET", "/api/recruitment/vacancies");

        assertThat(outcome.passedThrough()).isFalse();
        assertThat(outcome.response().getStatus()).isEqualTo(403);

        Map<String, Object> body = bodyOf(outcome.response());
        assertThat(body).containsEntry("code", "MODULE_NOT_IN_PLAN")
                .containsEntry("module", "recruitment")
                .containsEntry("requiredPlan", "STANDARD");
        assertThat((String) body.get("message")).contains("Upgrade to STANDARD");
    }

    @Test
    @DisplayName("a module the tenant switched off is 403 with a different, non-upsell reason")
    void tenantDisabledIsForbiddenWithoutUpsell() throws Exception {
        onPlan(Plan.LITE);
        disable("time-attendance");

        Outcome outcome = call("GET", "/api/attendance/events");

        assertThat(outcome.response().getStatus()).isEqualTo(403);
        Map<String, Object> body = bodyOf(outcome.response());
        assertThat(body).containsEntry("code", "MODULE_DISABLED")
                .containsEntry("module", "time-attendance")
                .doesNotContainKey("requiredPlan");
    }

    @Test
    @DisplayName("self-service and the admin surface answer on every plan")
    void alwaysOnNeverBlocked() throws Exception {
        onPlan(Plan.LITE);
        disable("self-service", "platform-admin");

        assertThat(call("GET", "/api/me").passedThrough()).isTrue();
        assertThat(call("GET", "/api/self/policies").passedThrough()).isTrue();
        assertThat(call("GET", "/api/settings").passedThrough()).isTrue();
        assertThat(call("GET", "/api/module-settings").passedThrough()).isTrue();
    }

    @Test
    @DisplayName("payslip self-service survives a tenant switching payroll off")
    void selfServiceSurvivesPayrollOff() throws Exception {
        onPlan(Plan.LITE);
        disable("payroll");

        // Admin payroll screens stop answering...
        assertThat(call("GET", "/api/payroll/runs").response().getStatus()).isEqualTo(403);
        // ...but an employee's own payslips are their own data, not a module.
        assertThat(call("GET", "/api/self/payslips").passedThrough()).isTrue();
    }

    @Test
    @DisplayName("shared reference data is never gated")
    void sharedPathsPassThrough() throws Exception {
        onPlan(Plan.LITE);

        assertThat(call("GET", "/api/holidays").passedThrough()).isTrue();
        assertThat(call("GET", "/api/attachments/7").passedThrough()).isTrue();
        assertThat(call("GET", "/api/notifications").passedThrough()).isTrue();
    }

    @Test
    @DisplayName("non-API paths, public endpoints and CORS preflight are untouched")
    void nonApiAndPublicUntouched() throws Exception {
        onPlan(Plan.LITE);

        assertThat(call("GET", "/home").passedThrough()).isTrue();
        assertThat(call("GET", "/actuator/health").passedThrough()).isTrue();
        assertThat(call("GET", "/api/public/careers/jobs").passedThrough()).isTrue();
        // Preflight carries no credentials — the CORS layer owns it.
        assertThat(call("OPTIONS", "/api/recruitment/vacancies").passedThrough()).isTrue();
    }

    @Test
    @DisplayName("writes are gated too, not just reads")
    void writesAreGated() throws Exception {
        onPlan(Plan.LITE);

        assertThat(call("POST", "/api/recruitment/vacancies").response().getStatus())
                .isEqualTo(403);
        assertThat(call("DELETE", "/api/compensation/pay-bands/3").response().getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("upgrading the plan opens the module up")
    void upgradeOpensModule() throws Exception {
        onPlan(Plan.STANDARD);

        assertThat(call("GET", "/api/recruitment/vacancies").passedThrough()).isTrue();
        // ...while an ENTERPRISE-only module still 403s on STANDARD.
        assertThat(call("GET", "/api/talent/pools").response().getStatus()).isEqualTo(403);
    }
}
