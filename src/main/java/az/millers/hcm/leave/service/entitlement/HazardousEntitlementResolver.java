package az.millers.hcm.leave.service.entitlement;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import az.millers.hcm.leave.domain.EntitlementComponentCode;
import az.millers.hcm.staffing.domain.Position;

/**
 * M151 — Art. 115.2 uplift for harmful/hazardous working conditions.
 *
 * <p>Driven by the position, not the employee or the work location. The
 * customer's register makes the distinction concrete: all 18 recipients work
 * offshore, but 21 other offshore staff receive nothing — the recipients are
 * Riggers, Welders, Mechanics and Electricians, i.e. specific trades on the
 * statutory list, spanning both Labour and Specialist grades.
 *
 * <p>Keying off the position means the entitlement follows a transfer without
 * anyone remembering to clear a flag, which is the failure mode that leaves a
 * desk-based ex-welder accruing hazard days for years.
 */
@Component
public class HazardousEntitlementResolver implements EntitlementComponentResolver {

    @Override
    public Optional<ResolvedComponent> resolve(EntitlementContext ctx) {
        Position position = ctx.position();
        if (position == null) {
            // No position assigned — nothing to inherit. Omitted rather than
            // shown as zero: "not applicable" is not the same claim as
            // "considered and granted nothing".
            return Optional.empty();
        }
        if (!position.isHazardous()) {
            return Optional.empty();
        }

        BigDecimal days = position.getHazardousLeaveDays();
        if (days == null) {
            // The DB CHECK makes this unreachable through normal writes; if it
            // ever happens the honest answer is a visible zero with the reason
            // attached, not a silent omission that looks like "not hazardous".
            return Optional.of(new ResolvedComponent(
                    EntitlementComponentCode.HAZARDOUS, BigDecimal.ZERO,
                    "Position " + position.getCode()
                            + " is flagged hazardous but carries no day count"));
        }

        return Optional.of(new ResolvedComponent(
                EntitlementComponentCode.HAZARDOUS, days,
                "Hazardous position: " + position.getTitle()
                        + " (" + position.getCode() + ")"));
    }
}
