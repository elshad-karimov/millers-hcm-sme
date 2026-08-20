package az.millers.hcm.config.plan;

/**
 * Published when a tenant moves between editions, so caches derived from the
 * plan (the resolved module set) drop their stale entry immediately rather than
 * serving the old entitlement until a restart.
 *
 * @param tenantId the tenant whose plan changed
 * @param previous plan before the change
 * @param current  plan after the change
 */
public record TenantPlanChangedEvent(String tenantId, Plan previous, Plan current) {}
