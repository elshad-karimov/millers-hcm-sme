package az.millers.hcm.leave.service.entitlement;

import java.util.Optional;

/**
 * M151 — derives one statutory component of an annual leave entitlement.
 *
 * <p>One implementation per statutory rule. Each is a pure function of the
 * {@link EntitlementContext}: no queries, no clock reads, no writes. That is
 * what makes the entitlement math testable at its boundaries, which matters
 * because leave-balance errors are silent — nobody notices being under-granted
 * two days until they try to book them.
 *
 * <p>Components with no derivable driver (blood donation) have no resolver;
 * they exist only as {@code MANUAL} rows.
 */
public interface EntitlementComponentResolver {

    /**
     * @return the component this resolver produces, or empty when it does not
     *         apply at all. Empty and a zero-day result are different: zero
     *         means "considered and granted nothing" and is shown on the
     *         breakdown, empty means "not applicable to this employee" and is
     *         omitted entirely.
     */
    Optional<ResolvedComponent> resolve(EntitlementContext ctx);
}
