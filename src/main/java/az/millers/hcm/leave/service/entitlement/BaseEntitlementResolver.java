package az.millers.hcm.leave.service.entitlement;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import az.millers.hcm.leave.domain.EntitlementComponentCode;

/**
 * M151 — Art. 114 base annual leave, keyed by the employee's position
 * classification.
 *
 * <p>Established from the customer's register: every one of the 118
 * Specialists carries 30 days and 15 of the 16 Labour employees carry 21. The
 * single Specialist sitting at 21 is treated as a data error rather than a
 * rule — one exception across 134 rows does not describe a policy — and will
 * surface as a difference the moment their entitlement is recalculated.
 *
 * <p>The mapping is tenant config ({@code LeaveType.baseDaysByClassification})
 * because "Specialist" and "Labour" are the customer's own taxonomy, not
 * anything the Labour Code names.
 */
@Component
public class BaseEntitlementResolver implements EntitlementComponentResolver {

    @Override
    public Optional<ResolvedComponent> resolve(EntitlementContext ctx) {
        Map<String, BigDecimal> byClassification =
                ctx.leaveType().getBaseDaysByClassification();

        // No mapping configured — the type opted into components but nobody
        // told us the base schedule. Fall back to the type's flat default so
        // an employee is never left with a zero entitlement by omission.
        if (byClassification == null || byClassification.isEmpty()) {
            BigDecimal fallback = ctx.leaveType().getDefaultAnnualEntitlementDays();
            if (fallback == null) return Optional.empty();
            return Optional.of(new ResolvedComponent(
                    EntitlementComponentCode.BASE, fallback,
                    "Leave type default (no base-days-by-classification configured)"));
        }

        String classification = ctx.positionClassification();
        if (classification == null || classification.isBlank()) {
            BigDecimal fallback = ctx.leaveType().getDefaultAnnualEntitlementDays();
            if (fallback == null) return Optional.empty();
            return Optional.of(new ResolvedComponent(
                    EntitlementComponentCode.BASE, fallback,
                    "Leave type default (employee has no position classification)"));
        }

        BigDecimal days = lookupIgnoreCase(byClassification, classification);
        if (days == null) {
            // A classification the schedule doesn't cover. Falling back to the
            // flat default here would quietly grant Specialist days to someone
            // whose grade nobody has priced, so say so in the basis instead —
            // it shows up on the breakdown as a line to fix.
            BigDecimal fallback = ctx.leaveType().getDefaultAnnualEntitlementDays();
            if (fallback == null) return Optional.empty();
            return Optional.of(new ResolvedComponent(
                    EntitlementComponentCode.BASE, fallback,
                    "Leave type default — classification \"" + classification
                            + "\" is not in the base-days schedule"));
        }

        return Optional.of(new ResolvedComponent(
                EntitlementComponentCode.BASE, days, classification));
    }

    /**
     * Classification is free text typed by HR, so "Specialist" and
     * "specialist" must not resolve to different entitlements.
     */
    private static BigDecimal lookupIgnoreCase(Map<String, BigDecimal> map, String key) {
        BigDecimal exact = map.get(key);
        if (exact != null) return exact;
        for (Map.Entry<String, BigDecimal> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key.trim())) {
                return e.getValue();
            }
        }
        return null;
    }
}
