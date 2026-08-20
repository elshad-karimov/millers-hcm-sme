package az.millers.hcm.leave.service.entitlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.DependentRelationship;
import az.millers.hcm.corehr.domain.EmployeeDependent;
import az.millers.hcm.leave.domain.EntitlementComponentCode;

/**
 * M151 — Art. 117 uplift for women with children, derived from dependent
 * records.
 *
 * <p>Tiers:
 * <ul>
 *   <li>a child with a disability under 16 → {@value #DISABLED_CHILD_DAYS} days</li>
 *   <li>three or more children under 14 → {@value #DISABLED_CHILD_DAYS} days</li>
 *   <li>exactly two children under 14 → {@value #TWO_CHILDREN_DAYS} days</li>
 *   <li>fewer than two → none</li>
 * </ul>
 *
 * <p>Ages are taken at {@link EntitlementContext#asOf()} — the first day of
 * the leave year — so a child turning 14 in June does not shrink a balance
 * their parent may already have booked against. The uplift drops the
 * following January instead.
 *
 * <p>The customer's register only ever shows the two-day tier, but the higher
 * tier is implemented because deriving from dependent data means it will fire
 * on its own as soon as a qualifying family is recorded, and a rule that
 * exists in law but not in code under-grants silently.
 *
 * <p><b>Scope note:</b> gated on the employee being female, matching both the
 * statute's wording and the register (all 10 recipients are women). Some
 * readings extend the entitlement to a father raising children alone; there is
 * no "sole carer" flag on the employee record to key that off, so such a case
 * has to be granted as a MANUAL component today.
 */
@Component
public class ChildrenEntitlementResolver implements EntitlementComponentResolver {

    static final BigDecimal TWO_CHILDREN_DAYS = new BigDecimal("2");
    static final BigDecimal DISABLED_CHILD_DAYS = new BigDecimal("5");

    /** Art. 117 age ceiling for counting a child towards the tiers. */
    static final int CHILD_AGE_LIMIT = 14;
    /** Higher ceiling that applies to a child with a disability. */
    static final int DISABLED_CHILD_AGE_LIMIT = 16;

    @Override
    public Optional<ResolvedComponent> resolve(EntitlementContext ctx) {
        if (!isFemale(ctx.employee().getGender())) {
            return Optional.empty();
        }

        List<EmployeeDependent> children = ctx.dependents() == null ? List.of()
                : ctx.dependents().stream()
                        .filter(d -> d.getRelationshipType() == DependentRelationship.CHILD)
                        .filter(EmployeeDependent::isActive)
                        .toList();

        if (children.isEmpty()) {
            return Optional.empty();
        }

        LocalDate asOf = ctx.asOf();

        // A dependent with no date of birth cannot be aged. Counting them
        // would over-grant and ignoring them silently would under-grant, so
        // they are tallied and named in the basis for HR to complete.
        long undated = children.stream().filter(d -> d.getDateOfBirth() == null).count();

        boolean hasDisabledChild = children.stream()
                .filter(d -> Boolean.TRUE.equals(d.getHasDisability()))
                .anyMatch(d -> ageUnder(d, asOf, DISABLED_CHILD_AGE_LIMIT));

        long under14 = children.stream()
                .filter(d -> ageUnder(d, asOf, CHILD_AGE_LIMIT))
                .count();

        String suffix = undated == 0 ? ""
                : " (" + undated + " dependent" + (undated == 1 ? "" : "s")
                  + " excluded — no date of birth on file)";

        if (hasDisabledChild) {
            return Optional.of(new ResolvedComponent(
                    EntitlementComponentCode.CHILDREN, DISABLED_CHILD_DAYS,
                    "Child with a disability under " + DISABLED_CHILD_AGE_LIMIT + suffix));
        }
        if (under14 >= 3) {
            return Optional.of(new ResolvedComponent(
                    EntitlementComponentCode.CHILDREN, DISABLED_CHILD_DAYS,
                    under14 + " children under " + CHILD_AGE_LIMIT
                            + " as at " + asOf + suffix));
        }
        if (under14 == 2) {
            return Optional.of(new ResolvedComponent(
                    EntitlementComponentCode.CHILDREN, TWO_CHILDREN_DAYS,
                    "2 children under " + CHILD_AGE_LIMIT + " as at " + asOf + suffix));
        }

        // One child or none: show the zero rather than omitting it, so the
        // breakdown records that the rule was evaluated. Without this line a
        // reader cannot tell "assessed, doesn't qualify" from "never checked".
        return Optional.of(new ResolvedComponent(
                EntitlementComponentCode.CHILDREN, BigDecimal.ZERO,
                under14 + " child" + (under14 == 1 ? "" : "ren") + " under "
                        + CHILD_AGE_LIMIT + " — tier starts at 2" + suffix));
    }

    private static boolean ageUnder(EmployeeDependent d, LocalDate asOf, int limit) {
        LocalDate dob = d.getDateOfBirth();
        if (dob == null) return false;
        return ChronoUnit.YEARS.between(dob, asOf) < limit;
    }

    /**
     * {@code gender} is free text on the employee record, so this tolerates
     * the spellings that actually occur ("Female", "female", "F") rather than
     * matching one exact string and silently dropping the entitlement for
     * everyone entered differently.
     */
    private static boolean isFemale(String gender) {
        if (gender == null) return false;
        String g = gender.trim();
        return g.equalsIgnoreCase("female") || g.equalsIgnoreCase("f");
    }
}
