package az.millers.hcm.leave.service.entitlement;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import az.millers.hcm.leave.api.dto.ExperienceBracket;
import az.millers.hcm.leave.domain.EntitlementComponentCode;

/**
 * M151 — Art. 116.1 uplift for length of professional experience.
 *
 * <p>Brackets on <em>total professional experience</em>, not company tenure.
 * That distinction was settled empirically against the customer's register:
 * company-tenure brackets reproduced 19 of 136 rows, professional-experience
 * brackets 126 of 131. Their schedule is 5/10/15 years → 2/4/6 days.
 *
 * <p>The uplift is evaluated at {@link EntitlementContext#asOf()}, the first
 * day of the leave year, so crossing a threshold mid-year takes effect the
 * following January. This is deliberate: entitlement that moves under a
 * balance an employee has already booked against is a support ticket at best
 * and an overdrawn balance at worst.
 */
@Component
public class SeniorityEntitlementResolver implements EntitlementComponentResolver {

    @Override
    public Optional<ResolvedComponent> resolve(EntitlementContext ctx) {
        List<ExperienceBracket> brackets = ctx.leaveType().getExperienceBrackets();
        if (brackets == null || brackets.isEmpty()) {
            // The type doesn't run an experience uplift at all.
            return Optional.empty();
        }

        BigDecimal experience = ctx.employee().getProfessionalExperienceYears();
        if (experience == null) {
            // Recorded as unknown rather than assumed zero: the employee may
            // well qualify, and a visible "not recorded" line prompts HR to
            // fill it in. Assuming zero would silently under-grant.
            return Optional.of(new ResolvedComponent(
                    EntitlementComponentCode.SENIORITY, BigDecimal.ZERO,
                    "Professional experience not recorded — no uplift applied"));
        }

        // Highest bracket whose threshold has been reached. Ordering by
        // minYears means the schedule needs no upper bounds and cannot leave
        // an uncovered gap between tiers.
        Optional<ExperienceBracket> match = brackets.stream()
                .filter(b -> BigDecimal.valueOf(b.minYears()).compareTo(experience) <= 0)
                .max(Comparator.comparingInt(ExperienceBracket::minYears));

        if (match.isEmpty()) {
            int lowest = brackets.stream()
                    .mapToInt(ExperienceBracket::minYears).min().orElse(0);
            return Optional.of(new ResolvedComponent(
                    EntitlementComponentCode.SENIORITY, BigDecimal.ZERO,
                    stripTrailingZeros(experience) + " yrs experience — below the "
                            + lowest + "-year threshold"));
        }

        ExperienceBracket bracket = match.get();
        return Optional.of(new ResolvedComponent(
                EntitlementComponentCode.SENIORITY,
                bracket.days(),
                stripTrailingZeros(experience) + " yrs professional experience → "
                        + bracket.minYears() + "-year bracket (as at "
                        + ctx.asOf() + ")"));
    }

    /** 8.50 reads badly in a justification; 8.5 and 15 read correctly. */
    private static String stripTrailingZeros(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
