package az.millers.hcm.config.plan;

/**
 * Quantitative ceilings that come with a {@link Plan}.
 *
 * <p>{@code null} means unlimited. Every tier ships unlimited today — the
 * enforcement path ({@link PlanLimitGate}) is wired and tested so switching a
 * tier to a real number is a one-line change in {@link Plan} once pricing is
 * decided, with no new code.
 *
 * @param maxActiveEmployees ceiling on employees in a non-terminated status;
 *                           counting active-only means a tenant is never blocked
 *                           by its own leaver history.
 */
public record PlanLimits(Integer maxActiveEmployees) {

    public static PlanLimits unlimited() {
        return new PlanLimits(null);
    }

    public boolean hasEmployeeLimit() {
        return maxActiveEmployees != null;
    }
}
