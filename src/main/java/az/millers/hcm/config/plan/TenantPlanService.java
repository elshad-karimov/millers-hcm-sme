package az.millers.hcm.config.plan;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.tenant.Tenant;
import az.millers.hcm.common.tenant.TenantContext;
import az.millers.hcm.common.tenant.TenantRepository;

/**
 * Reads (and caches) the {@link Plan} a tenant is on.
 *
 * <p>Consulted on every {@code /api} request through {@link ModuleAccessFilter},
 * so the lookup is served from a small in-memory map rather than hitting
 * {@code config.tenant} per request. The plan changes only through
 * {@link #changePlan} or provisioning, both of which evict.
 *
 * <p>Unknown tenant → {@link Plan#defaultPlan()}. That is the conservative
 * direction for this SME edition: an unregistered tenant gets the lean plan
 * rather than the full product.
 */
@Service
public class TenantPlanService {

    private static final Logger log = LoggerFactory.getLogger(TenantPlanService.class);

    private final TenantRepository tenants;
    private final ApplicationEventPublisher events;

    /** tenantId -> plan. */
    private final Map<String, Plan> cache = new ConcurrentHashMap<>();

    public TenantPlanService(TenantRepository tenants, ApplicationEventPublisher events) {
        this.tenants = tenants;
        this.events = events;
    }

    /** Plan of the tenant bound to the current request. */
    public Plan currentPlan() {
        return planFor(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public Plan planFor(String tenantId) {
        return cache.computeIfAbsent(tenantId, id -> tenants.findById(id)
                .map(Tenant::getPlan)
                .orElseGet(() -> {
                    log.warn("No tenant row for '{}' — defaulting to {}", id, Plan.defaultPlan());
                    return Plan.defaultPlan();
                }));
    }

    /**
     * Move a tenant to another plan.
     *
     * <p>A downgrade never deletes data — modules simply stop answering, and the
     * rows stay put so an upgrade restores full access. Callers are responsible
     * for the audit entry (see {@code TenantAdminController}).
     */
    @Transactional
    public Plan changePlan(String tenantId, Plan plan, String actor) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow(
                () -> new IllegalArgumentException("Unknown tenant: " + tenantId));
        Plan previous = tenant.getPlan();
        tenant.setPlan(plan);
        tenant.setUpdatedBy(actor);
        tenants.save(tenant);
        evict(tenantId);
        events.publishEvent(new TenantPlanChangedEvent(tenantId, previous, plan));
        log.info("Tenant '{}' plan {} -> {} by {}", tenantId, previous, plan, actor);
        return previous;
    }

    public void evict(String tenantId) {
        cache.remove(tenantId);
    }

    public void evictAll() {
        cache.clear();
    }
}
