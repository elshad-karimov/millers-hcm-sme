package az.millers.hcm.leave.service.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import az.millers.hcm.corehr.domain.DependentRelationship;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmployeeDependent;
import az.millers.hcm.leave.api.dto.ExperienceBracket;
import az.millers.hcm.leave.domain.EntitlementComponentCode;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.staffing.domain.Position;

/**
 * M151 — the statutory entitlement math, pinned at its boundaries.
 *
 * <p>Leave-balance errors are silent: nobody notices being under-granted two
 * days until they try to book them, and by then the year is half gone. Every
 * threshold in the rules is therefore tested on both sides.
 *
 * <p>The expected values come from the customer's own personnel register
 * (137 worked examples), not from a reading of the statute — where the two
 * disagreed, the register won and the disagreement is noted.
 */
class EntitlementResolverTest {

    private static final LocalDate LEAVE_YEAR_START = LocalDate.of(2026, 1, 1);

    // ── Base (Art. 114) ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Base entitlement")
    class Base {

        private final BaseEntitlementResolver resolver = new BaseEntitlementResolver();

        @Test
        @DisplayName("Specialist gets 30 and Labour 21 — the register's split")
        void classificationDrivesBaseDays() {
            assertThat(daysFor("Specialist")).isEqualByComparingTo("30");
            assertThat(daysFor("Labour")).isEqualByComparingTo("21");
        }

        @Test
        @DisplayName("classification match is case-insensitive — HR types it by hand")
        void classificationLookupIgnoresCase() {
            assertThat(daysFor("specialist")).isEqualByComparingTo("30");
            assertThat(daysFor("LABOUR")).isEqualByComparingTo("21");
        }

        @Test
        @DisplayName("an unpriced classification falls back to the default and says so")
        void unknownClassificationIsFlaggedInTheBasis() {
            ResolvedComponent c = resolve("Director").orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("25");
            assertThat(c.basis()).contains("Director").contains("not in the base-days schedule");
        }

        @Test
        @DisplayName("an employee with no classification falls back rather than getting zero")
        void missingClassificationFallsBack() {
            ResolvedComponent c = resolve(null).orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("25");
            assertThat(c.basis()).contains("no position classification");
        }

        private BigDecimal daysFor(String classification) {
            return resolve(classification).orElseThrow().days();
        }

        private Optional<ResolvedComponent> resolve(String classification) {
            Employee e = employee();
            e.setPositionClassification(classification);
            LeaveType t = leaveType();
            t.setBaseDaysByClassification(Map.of(
                    "Specialist", new BigDecimal("30"),
                    "Labour", new BigDecimal("21")));
            t.setDefaultAnnualEntitlementDays(new BigDecimal("25"));
            return resolver.resolve(ctx(e, t, null, List.of()));
        }
    }

    // ── Seniority (Art. 116.1) ──────────────────────────────────────────

    @Nested
    @DisplayName("Seniority uplift")
    class Seniority {

        private final SeniorityEntitlementResolver resolver = new SeniorityEntitlementResolver();

        @Test
        @DisplayName("brackets at 5/10/15 years give 2/4/6 days")
        void bracketsMatchTheRegister() {
            assertThat(daysFor("5")).isEqualByComparingTo("2");
            assertThat(daysFor("10")).isEqualByComparingTo("4");
            assertThat(daysFor("15")).isEqualByComparingTo("6");
        }

        @Test
        @DisplayName("thresholds are inclusive — exactly 10 years is the 10-year bracket, not the 5")
        void thresholdsAreInclusive() {
            assertThat(daysFor("9.9")).isEqualByComparingTo("2");
            assertThat(daysFor("10")).isEqualByComparingTo("4");
            assertThat(daysFor("14.99")).isEqualByComparingTo("4");
            assertThat(daysFor("15")).isEqualByComparingTo("6");
        }

        @Test
        @DisplayName("below the lowest threshold there is no uplift")
        void belowLowestBracketGivesZero() {
            assertThat(daysFor("0")).isEqualByComparingTo("0");
            assertThat(daysFor("4.99")).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("experience well past the top bracket stays at the top bracket")
        void aboveTopBracketStaysAtTop() {
            assertThat(daysFor("40")).isEqualByComparingTo("6");
        }

        @Test
        @DisplayName("unrecorded experience grants nothing but says so, rather than assuming zero years")
        void unrecordedExperienceIsVisible() {
            Employee e = employee();
            e.setProfessionalExperienceYears(null);
            ResolvedComponent c = resolver.resolve(ctx(e, typeWithBrackets(), null, List.of()))
                    .orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("0");
            assertThat(c.basis()).contains("not recorded");
        }

        @Test
        @DisplayName("a type with no bracket schedule produces no component at all")
        void noScheduleMeansNoComponent() {
            Employee e = employee();
            e.setProfessionalExperienceYears(new BigDecimal("20"));
            assertThat(resolver.resolve(ctx(e, leaveType(), null, List.of()))).isEmpty();
        }

        @Test
        @DisplayName("the basis names the evaluation date, because the bracket is fixed at 1 January")
        void basisRecordsTheEvaluationDate() {
            ResolvedComponent c = resolve("12").orElseThrow();
            assertThat(c.basis()).contains("2026-01-01");
        }

        private BigDecimal daysFor(String years) {
            return resolve(years).orElseThrow().days();
        }

        private Optional<ResolvedComponent> resolve(String years) {
            Employee e = employee();
            e.setProfessionalExperienceYears(new BigDecimal(years));
            return resolver.resolve(ctx(e, typeWithBrackets(), null, List.of()));
        }

        private LeaveType typeWithBrackets() {
            LeaveType t = leaveType();
            t.setExperienceBrackets(List.of(
                    new ExperienceBracket(5, new BigDecimal("2")),
                    new ExperienceBracket(10, new BigDecimal("4")),
                    new ExperienceBracket(15, new BigDecimal("6"))));
            return t;
        }
    }

    // ── Hazardous (Art. 115.2) ──────────────────────────────────────────

    @Nested
    @DisplayName("Harmful working conditions")
    class Hazardous {

        private final HazardousEntitlementResolver resolver = new HazardousEntitlementResolver();

        @Test
        @DisplayName("a hazardous position grants its day count")
        void hazardousPositionGrantsDays() {
            Position p = position(true, new BigDecimal("6"));
            ResolvedComponent c = resolver.resolve(ctx(employee(), leaveType(), p, List.of()))
                    .orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("6");
            assertThat(c.basis()).contains("Structural Welder");
        }

        @Test
        @DisplayName("a non-hazardous position produces nothing — not a zero line")
        void nonHazardousPositionProducesNothing() {
            Position p = position(false, null);
            assertThat(resolver.resolve(ctx(employee(), leaveType(), p, List.of()))).isEmpty();
        }

        @Test
        @DisplayName("an employee with no position produces nothing")
        void noPositionProducesNothing() {
            assertThat(resolver.resolve(ctx(employee(), leaveType(), null, List.of()))).isEmpty();
        }

        @Test
        @DisplayName("a hazardous position missing its day count is surfaced, not silently skipped")
        void hazardousWithoutDaysIsVisible() {
            // Unreachable through the API (a DB CHECK forbids it), but if it
            // ever occurs the breakdown must not look like "not hazardous".
            Position p = position(true, null);
            ResolvedComponent c = resolver.resolve(ctx(employee(), leaveType(), p, List.of()))
                    .orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("0");
            assertThat(c.basis()).contains("no day count");
        }
    }

    // ── Children (Art. 117) ─────────────────────────────────────────────

    @Nested
    @DisplayName("Women with children")
    class Children {

        private final ChildrenEntitlementResolver resolver = new ChildrenEntitlementResolver();

        @Test
        @DisplayName("two children under 14 give 2 days — the register's only observed tier")
        void twoChildrenGiveTwoDays() {
            assertThat(daysFor("Female", child(2015), child(2018))).isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("three children under 14 escalate to 5 days")
        void threeChildrenGiveFiveDays() {
            assertThat(daysFor("Female", child(2015), child(2018), child(2020)))
                    .isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("a child with a disability under 16 gives 5 days on its own")
        void disabledChildGivesFiveDays() {
            EmployeeDependent disabled = child(2012);
            disabled.setHasDisability(true);
            assertThat(daysFor("Female", disabled)).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("one child is below the tier and shows as an explicit zero")
        void oneChildGivesZeroButIsRecorded() {
            ResolvedComponent c = resolve("Female", child(2018)).orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("0");
            assertThat(c.basis()).contains("tier starts at 2");
        }

        @Test
        @DisplayName("age is taken at 1 January — a child turning 14 in June still counts this year")
        void ageIsEvaluatedAtTheStartOfTheLeaveYear() {
            // Born June 2012 → 13 on 1 Jan 2026, 14 in June 2026. Counting at
            // 1 January keeps the entitlement stable for the whole leave year.
            EmployeeDependent turning14MidYear = child(LocalDate.of(2012, 6, 15));
            assertThat(daysFor("Female", turning14MidYear, child(2018)))
                    .isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("a child already 14 on 1 January no longer counts")
        void childAlreadyOverTheLimitDoesNotCount() {
            EmployeeDependent alreadyFourteen = child(LocalDate.of(2011, 12, 31));
            ResolvedComponent c = resolve("Female", alreadyFourteen, child(2018)).orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("the disability tier stops at 16, not 14")
        void disabilityTierUsesTheHigherAgeLimit() {
            EmployeeDependent fifteen = child(LocalDate.of(2010, 6, 1));   // 15 at 1 Jan 2026
            fifteen.setHasDisability(true);
            assertThat(daysFor("Female", fifteen)).isEqualByComparingTo("5");

            EmployeeDependent seventeen = child(LocalDate.of(2008, 6, 1)); // 17 at 1 Jan 2026
            seventeen.setHasDisability(true);
            ResolvedComponent c = resolve("Female", seventeen).orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("men produce no component — the entitlement is gendered in statute and register")
        void menProduceNoComponent() {
            assertThat(resolve("Male", child(2015), child(2018))).isEmpty();
        }

        @Test
        @DisplayName("gender spelling variations still resolve")
        void genderMatchIsTolerant() {
            assertThat(daysFor("female", child(2015), child(2018))).isEqualByComparingTo("2");
            assertThat(daysFor("F", child(2015), child(2018))).isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("inactive dependents are ignored")
        void inactiveDependentsAreIgnored() {
            EmployeeDependent inactive = child(2018);
            inactive.setActive(false);
            ResolvedComponent c = resolve("Female", child(2015), inactive).orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("a dependent with no date of birth is excluded and called out")
        void undatedDependentIsCalledOut() {
            EmployeeDependent undated = child(2018);
            undated.setDateOfBirth(null);
            ResolvedComponent c = resolve("Female", child(2015), undated).orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("0");
            assertThat(c.basis()).contains("no date of birth");
        }

        @Test
        @DisplayName("non-child dependents do not count towards the tiers")
        void spouseDoesNotCount() {
            EmployeeDependent spouse = child(1990);
            spouse.setRelationshipType(DependentRelationship.SPOUSE);
            ResolvedComponent c = resolve("Female", child(2015), spouse).orElseThrow();
            assertThat(c.days()).isEqualByComparingTo("0");
        }

        private BigDecimal daysFor(String gender, EmployeeDependent... deps) {
            return resolve(gender, deps).orElseThrow().days();
        }

        private Optional<ResolvedComponent> resolve(String gender, EmployeeDependent... deps) {
            Employee e = employee();
            e.setGender(gender);
            return resolver.resolve(ctx(e, leaveType(), null, List.of(deps)));
        }
    }

    // ── What counts toward the annual total ─────────────────────────────

    @Nested
    @DisplayName("Annual-total membership")
    class TotalMembership {

        @Test
        @DisplayName("blood-donation days are recorded but excluded from the vacation total")
        void bloodDonationDoesNotCount() {
            assertThat(EntitlementComponentCode.BLOOD_DONATION.countsTowardAnnualEntitlement())
                    .isFalse();
        }

        @Test
        @DisplayName("every statutory vacation component counts")
        void statutoryComponentsCount() {
            assertThat(EntitlementComponentCode.BASE.countsTowardAnnualEntitlement()).isTrue();
            assertThat(EntitlementComponentCode.SENIORITY.countsTowardAnnualEntitlement()).isTrue();
            assertThat(EntitlementComponentCode.HAZARDOUS.countsTowardAnnualEntitlement()).isTrue();
            assertThat(EntitlementComponentCode.CHILDREN.countsTowardAnnualEntitlement()).isTrue();
            assertThat(EntitlementComponentCode.OTHER.countsTowardAnnualEntitlement()).isTrue();
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────

    private static EntitlementContext ctx(Employee e, LeaveType t, Position p,
                                           List<EmployeeDependent> deps) {
        return new EntitlementContext(e, t, 2026, p, new ArrayList<>(deps), LEAVE_YEAR_START);
    }

    private static Employee employee() {
        Employee e = new Employee();
        e.setId(java.util.UUID.randomUUID());
        e.setFirstName("Test");
        e.setLastName("Employee");
        e.setHireDate(LocalDate.of(2024, 1, 1));
        return e;
    }

    private static LeaveType leaveType() {
        LeaveType t = new LeaveType();
        t.setId(java.util.UUID.randomUUID());
        t.setCode("ANNUAL");
        t.setName("Annual leave");
        t.setEntitlementComponentsEnabled(true);
        return t;
    }

    private static Position position(boolean hazardous, BigDecimal days) {
        Position p = new Position();
        p.setId(java.util.UUID.randomUUID());
        p.setCode("POS-001");
        p.setTitle("Structural Welder");
        p.setHazardous(hazardous);
        p.setHazardousLeaveDays(days);
        return p;
    }

    private static EmployeeDependent child(int birthYear) {
        return child(LocalDate.of(birthYear, 1, 1));
    }

    private static EmployeeDependent child(LocalDate dob) {
        EmployeeDependent d = new EmployeeDependent();
        d.setId(java.util.UUID.randomUUID());
        d.setRelationshipType(DependentRelationship.CHILD);
        d.setFirstName("Child");
        d.setLastName("Employee");
        d.setDateOfBirth(dob);
        d.setActive(true);
        return d;
    }
}
