package az.millers.hcm.common.tenant;

/**
 * Request-scoped holder for the current tenant id (multi-tenancy Phase 1).
 *
 * <p>Populated per request by the tenant-resolution filter (Phase 3, from the
 * JWT issuer → tenant mapping). Until then it is unset and {@link #current()}
 * returns {@link #DEFAULT}, so behaviour is identical to the single-tenant app.
 *
 * <p>System / scheduler / async threads have no request context, so they also
 * see {@link #DEFAULT}; per-tenant background jobs must set the tenant explicitly
 * (Phase 4).
 */
public final class TenantContext {

    /** The bootstrap/single-tenant identifier the whole codebase used as a constant. */
    public static final String DEFAULT = "default";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    /** Set the tenant for the current thread. */
    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    /** Current tenant, or {@link #DEFAULT} when none is bound (system threads / pre-Phase-3). */
    public static String current() {
        String t = CURRENT.get();
        return t != null ? t : DEFAULT;
    }

    /** Whether a tenant is explicitly bound on this thread. */
    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    /** Clear the tenant binding — always call in a finally after a request/job. */
    public static void clear() {
        CURRENT.remove();
    }
}
