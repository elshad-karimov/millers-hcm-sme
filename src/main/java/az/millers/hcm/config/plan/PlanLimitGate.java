package az.millers.hcm.config.plan;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.repo.EmployeeRepository;

/**
 * Enforces the quantitative ceilings of a tenant's {@link Plan}.
 *
 * <p>Mirrors the existing {@code PositionHeadcountService} gate: services call
 * {@code assertCanAddEmployee()} before creating a person, and get a clear
 * upgrade message instead of a silent overrun.
 *
 * <p>Every tier is unlimited today ({@link PlanLimits}), so this is a no-op in
 * practice — deliberately: the path is wired and covered by tests, so putting a
 * real number on a tier later is a one-line change with no new code and no
 * untested branch.
 */
@Service
public class PlanLimitGate {

    private final TenantPlanService plans;
    private final EmployeeRepository employees;

    public PlanLimitGate(TenantPlanService plans, EmployeeRepository employees) {
        this.plans = plans;
        this.employees = employees;
    }

    /**
     * Thrown when a plan ceiling would be exceeded. Distinct from
     * {@code BadRequestException} so the SPA can render an upgrade prompt rather
     * than a validation error.
     */
    public static class PlanLimitExceededException extends RuntimeException {
        private final transient Plan plan;
        private final int limit;
        private final long current;

        public PlanLimitExceededException(String message, Plan plan, int limit, long current) {
            super(message);
            this.plan = plan;
            this.limit = limit;
            this.current = current;
        }

        public Plan plan() {
            return plan;
        }

        public int limit() {
            return limit;
        }

        public long current() {
            return current;
        }
    }

    /**
     * Guard before adding a person to the tenant.
     *
     * @throws PlanLimitExceededException when the tenant is already at its
     *                                    active-employee ceiling
     */
    @Transactional(readOnly = true)
    public void assertCanAddEmployee() {
        Plan plan = plans.currentPlan();
        assertCanAddEmployee(plan, plan.limits());
    }

    /**
     * The enforcement itself, with the limits passed in.
     *
     * <p>Package-visible so the blocking branch stays covered while every tier
     * is still unlimited — otherwise the first tier to get a real number would
     * be the first time this code ever ran.
     */
    void assertCanAddEmployee(Plan plan, PlanLimits limits) {
        if (!limits.hasEmployeeLimit()) {
            return;
        }
        int max = limits.maxActiveEmployees();
        long current = employees.countActiveEmployees();
        if (current >= max) {
            throw new PlanLimitExceededException(
                    "Your " + plan + " plan allows up to " + max + " active employees "
                            + "(currently " + current + "). Upgrade your plan to add more.",
                    plan, max, current);
        }
    }
}
