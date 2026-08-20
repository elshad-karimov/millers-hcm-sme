package az.millers.hcm.leave.service.entitlement;

import java.math.BigDecimal;

import az.millers.hcm.leave.domain.EntitlementComponentCode;

/**
 * M151 — one resolver's verdict for one component.
 *
 * @param code  which component this is
 * @param days  days granted; zero is a legitimate answer and is still stored,
 *              so the breakdown can show "harmful conditions: 0" rather than
 *              leaving the reader to wonder whether it was considered
 * @param basis human-readable justification rendered on the breakdown and in
 *              audit exports — never null, because a component nobody can
 *              explain is a component nobody can defend in an inspection
 */
public record ResolvedComponent(
        EntitlementComponentCode code,
        BigDecimal days,
        String basis) {

    public ResolvedComponent {
        if (code == null) throw new IllegalArgumentException("code is required");
        if (days == null) throw new IllegalArgumentException("days is required");
        if (days.signum() < 0) {
            throw new IllegalArgumentException(
                    "A component cannot grant negative days: " + code + " = " + days);
        }
        if (basis == null || basis.isBlank()) {
            throw new IllegalArgumentException("basis is required for " + code);
        }
    }
}
