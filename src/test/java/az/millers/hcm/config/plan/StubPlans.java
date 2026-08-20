package az.millers.hcm.config.plan;

import java.util.HashMap;
import java.util.Map;

/**
 * Hand-written stand-in for {@link TenantPlanService}.
 *
 * <p>A plain subclass rather than a Mockito mock: the inline mock maker cannot
 * instrument this class on the JDK the build runs on, and a two-field stub is
 * clearer than the stubbing it replaces.
 */
class StubPlans extends TenantPlanService {

    /** Plan returned for any tenant unless {@link #perTenant} overrides it. */
    Plan uniform = Plan.LITE;

    final Map<String, Plan> perTenant = new HashMap<>();

    StubPlans() {
        super(null, event -> { /* no listeners in unit tests */ });
    }

    @Override
    public Plan planFor(String tenantId) {
        return perTenant.getOrDefault(tenantId, uniform);
    }
}
