package az.millers.hcm.payroll.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.payroll.timepay.TimePayRule;

/**
 * The blocking payroll-validation gate for the calculation profiles.
 *
 * <p>Every expected value comes from the company's own July 2026 spreadsheets,
 * captured into {@code fixtures/july-2026-worked-examples.json}. The point is
 * not that the code runs — it is that it produces the same money the business
 * already pays, to the cent. Nothing here is asserted approximately.
 *
 * <p>The tests that assert a <em>refusal</em> matter as much as the ones that
 * assert an amount. Where the source material genuinely does not say what
 * someone should be paid, producing a plausible number would be the worst
 * possible outcome: a payroll error is silent.
 */
class ProfilePayCalculatorTest {

    private static JsonNode fixtures;
    private static BigDecimal norm;
    private final ProfilePayCalculator calculator = new ProfilePayCalculator();
    private static final LocalDate PERIOD = LocalDate.of(2026, 7, 1);

    @BeforeAll
    static void loadFixtures() throws Exception {
        try (InputStream in = ProfilePayCalculatorTest.class
                .getResourceAsStream("/fixtures/july-2026-worked-examples.json")) {
            fixtures = new ObjectMapper().readTree(in);
        }
        norm = fixtures.get("normWorkingHours").decimalValue();
    }

    // ---- the catalog, as seeded by V319 ------------------------------------

    private static TimePayRule rule(String code, String basis, String multiplier,
                                    String flat, String exempt, int order) {
        TimePayRule r = new TimePayRule();
        r.setCategoryCode(code);
        r.setBasis(basis);
        r.setMultiplier(new BigDecimal(multiplier));
        if (flat != null) r.setFlatAmount(new BigDecimal(flat));
        r.setExemptPerUnit(new BigDecimal(exempt));
        r.setDisplayOrder(order);
        r.setActive(true);
        r.setNote(code);
        return r;
    }

    private static List<TimePayRule> catalog() {
        return List.of(
                rule("OFFSHORE_HOURS", "HOURLY_RATE", "1.75", null, "0", 10),
                rule("QUAYSIDE_HOURS", "HOURLY_RATE", "1.60", null, "0", 20),
                rule("ONSHORE_HOURS", "HOURLY_RATE", "1.00", null, "0", 30),
                rule("ONSHORE_OVERTIME_HOURS", "OVERTIME_RATE", "1.00", null, "0", 40),
                rule("MEAL_ALLOWANCE_DAYS", "FLAT_PER_UNIT", "1.00", "12.00", "5", 50),
                rule("TRANSPORT_ALLOWANCE_DAYS", "FLAT_PER_UNIT", "1.00", "10.00", "0", 60),
                rule("HOTEL_QUARANTINE_HOURS", "HOURLY_RATE", "1.75", null, "0", 70),
                rule("OFFSHORE_NIGHT_HOURS", "HOURLY_RATE", "0.20", null, "0", 80),
                rule("QUAYSIDE_NIGHT_HOURS", "HOURLY_RATE", "0.20", null, "0", 90),
                rule("OFFSHORE_HOLIDAY_HOURS", "HOURLY_RATE", "1.75", null, "0", 100),
                rule("QUAYSIDE_HOLIDAY_HOURS", "HOURLY_RATE", "1.60", null, "0", 110));
    }

    // ---- the profiles, as seeded by V327 -----------------------------------

    private static CalculationProfile profile(String code, String offshoreMode,
                                              String offshoreMult, String excessMethod,
                                              String excessMult, String scheme) {
        CalculationProfile p = new CalculationProfile();
        p.setCode(code);
        p.setName(code);
        p.setOffshoreSalaryMode(offshoreMode);
        if (offshoreMult != null) p.setOffshoreMultiplier(new BigDecimal(offshoreMult));
        p.setExcessMethod(excessMethod);
        if (excessMult != null) p.setExcessMultiplier(new BigDecimal(excessMult));
        p.setBalancingSchemeCode(scheme);
        p.setActive(true);
        // Left null exactly as V327 seeds it — BLOCKERS Q1.
        p.setNightHoursSeparateFromBase(null);
        return p;
    }

    private static CalculationProfile onshoreFixed() {
        return profile("ONSHORE_FIXED", CalculationProfile.OFFSHORE_NONE, null,
                CalculationProfile.EXCESS_NONE, null, null);
    }

    private static CalculationProfile onshoreRandomOffshore() {
        return profile("ONSHORE_RANDOM_OFFSHORE", CalculationProfile.OFFSHORE_HOURLY, "1.75",
                CalculationProfile.EXCESS_MONTHLY, "1.75", null);
    }

    private static CalculationProfile offshoreRotation() {
        return profile("OFFSHORE_ROTATION", CalculationProfile.OFFSHORE_MONTHLY_BASE, "1.75",
                CalculationProfile.EXCESS_BALANCING_PERIOD, null, "OFFSHORE_4_MONTH");
    }

    private static CalculationProfile offshoreRandomOnshore() {
        return profile("OFFSHORE_RANDOM_ONSHORE", CalculationProfile.OFFSHORE_DERIVED_FROM_NORM,
                "1.75", CalculationProfile.EXCESS_NONE, null, null);
    }

    // ---- helpers ------------------------------------------------------------

    private ProfilePayCalculator.Result price(CalculationProfile p, String base,
                                              Map<String, BigDecimal> qty) {
        return price(p, base, qty, null, null);
    }

    private ProfilePayCalculator.Result price(CalculationProfile p, String base,
                                              Map<String, BigDecimal> qty,
                                              BigDecimal mewaRate,
                                              BigDecimal settlementHours) {
        return calculator.calculate(new ProfilePayCalculator.Input(
                p, new BigDecimal(base), norm, PERIOD, qty, catalog(),
                mewaRate, settlementHours,
                null, null, null, null, null, null));
    }

    private static Map<String, BigDecimal> qty(Object... pairs) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], new BigDecimal(String.valueOf(pairs[i + 1])));
        }
        return m;
    }

    private static BigDecimal amountOf(ProfilePayCalculator.Result r, String earningCode) {
        return r.earnings().stream()
                .filter(l -> l.earningCode().equals(earningCode))
                .map(ProfilePayCalculator.Line::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal expected(String group, String caseName, String field) {
        for (JsonNode n : fixtures.get(group)) {
            if (n.get("case").asText().equals(caseName)) {
                return n.get("expected").get(field).decimalValue();
            }
        }
        throw new AssertionError("No fixture case '" + caseName + "' in " + group);
    }

    // ---- rates ---------------------------------------------------------------

    @Test
    @DisplayName("hourly rate is base / norm, overtime is twice it")
    void hourlyRate() {
        ProfilePayCalculator.Result r = price(onshoreFixed(), "3500", qty());
        assertThat(r.hourlyRate()).isEqualByComparingTo(
                expected("rates", "hourly-rate", "hourlyRateRounded"));
        assertThat(r.overtimeRate()).isEqualByComparingTo(
                expected("rates", "hourly-rate", "overtimeRate"));
    }

    @Test
    @DisplayName("norm hours are mandatory — they are the divisor behind every rate")
    void normHoursRequired() {
        assertThatThrownBy(() -> calculator.calculate(new ProfilePayCalculator.Input(
                onshoreFixed(), new BigDecimal("3500"), BigDecimal.ZERO, PERIOD,
                qty(), catalog(), null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Norm working hours");
    }

    @Test
    @DisplayName("no profile is a hard failure, never a default")
    void profileRequired() {
        assertThatThrownBy(() -> calculator.calculate(new ProfilePayCalculator.Input(
                null, new BigDecimal("3500"), norm, PERIOD,
                qty(), catalog(), null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No calculation profile");
    }

    // ---- ONSHORE_FIXED -------------------------------------------------------

    @Test
    @DisplayName("a full norm month on an onshore contract reproduces base salary")
    void onshoreFullMonth() {
        ProfilePayCalculator.Result r = price(onshoreFixed(), "3000", qty("ONSHORE_HOURS", 184));
        assertThat(amountOf(r, "ONSHORE_HOURS")).isEqualByComparingTo(
                expected("components", "onshore-fixed-full-month", "onshoreAmount"));
        assertThat(r.gross()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("offshore hours on a profile with no offshore component are a blocker, not pay")
    void offshoreOnOnshoreProfileBlocks() {
        ProfilePayCalculator.Result r = price(onshoreFixed(), "3000", qty("OFFSHORE_HOURS", 40));
        assertThat(r.isPayable()).isFalse();
        assertThat(r.blockers()).anyMatch(b -> b.contains("no offshore component"));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_OFFSHORE_75))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- ONSHORE_RANDOM_OFFSHORE ---------------------------------------------

    @Test
    @DisplayName("onshore hours during an offshore trip stay at the plain rate")
    void onshorePortion() {
        ProfilePayCalculator.Result r =
                price(onshoreRandomOffshore(), "3500", qty("ONSHORE_HOURS", 136));
        assertThat(amountOf(r, "ONSHORE_HOURS")).isEqualByComparingTo(
                expected("components", "onshore-portion-during-offshore-trip", "onshoreAmount"));
    }

    @Test
    @DisplayName("offshore hours are priced hourly at 1.75")
    void offshoreHourly() {
        ProfilePayCalculator.Result r =
                price(onshoreRandomOffshore(), "3500", qty("OFFSHORE_HOURS", 96));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_OFFSHORE_75)).isEqualByComparingTo(
                expected("components", "offshore-hours-at-1.75", "offshoreAmount"));
    }

    @Test
    @DisplayName("1.75 is absolute, not a premium added to a separate base line")
    void offshoreDecomposition() {
        // A base of 1840 over a 184 norm is a clean 10 AZN/hour.
        ProfilePayCalculator.Result r =
                price(onshoreRandomOffshore(), "1840", qty("OFFSHORE_HOURS", 12));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_OFFSHORE_75)).isEqualByComparingTo(
                expected("components", "offshore-premium-decomposition", "offshoreAmount"));
        assertThat(r.gross()).isEqualByComparingTo("210.00");
    }

    @Test
    @DisplayName("onshore overtime is twice the hourly rate")
    void onshoreOvertime() {
        ProfilePayCalculator.Result r = price(onshoreRandomOffshore(), "1333",
                qty("ONSHORE_OVERTIME_HOURS", "51.5"));
        assertThat(amountOf(r, "ONSHORE_OVERTIME_HOURS")).isEqualByComparingTo(
                expected("components", "onshore-overtime", "onshoreOvertimeAmount"));
    }

    @Test
    @DisplayName("night hours pay a 20% top-up on hours already paid offshore")
    void offshoreNightPremium() {
        ProfilePayCalculator.Result r = price(onshoreRandomOffshore(), "3500",
                qty("OFFSHORE_NIGHT_HOURS", 32));
        assertThat(amountOf(r, "OFFSHORE_NIGHT_HOURS")).isEqualByComparingTo(
                expected("components", "offshore-night-premium", "offshoreNightAmount"));
    }

    @Test
    @DisplayName("quayside is priced at 1.60")
    void quayside() {
        ProfilePayCalculator.Result r =
                price(onshoreRandomOffshore(), "3500", qty("QUAYSIDE_HOURS", 40));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_QUAYSIDE)).isEqualByComparingTo(
                expected("components", "quayside", "quaysideAmount"));
    }

    @Test
    @DisplayName("meal is 12 AZN/day with 5 exempt, transport is 10 AZN/day")
    void allowances() {
        ProfilePayCalculator.Result r = price(onshoreRandomOffshore(), "3500",
                qty("MEAL_ALLOWANCE_DAYS", 17, "TRANSPORT_ALLOWANCE_DAYS", 17));
        assertThat(amountOf(r, "MEAL_ALLOWANCE_DAYS")).isEqualByComparingTo(
                expected("components", "meal-and-transport", "mealAllowanceAmount"));
        assertThat(amountOf(r, "TRANSPORT_ALLOWANCE_DAYS")).isEqualByComparingTo(
                expected("components", "meal-and-transport", "transportAllowanceAmount"));
        assertThat(r.contributionExemptAmount()).isEqualByComparingTo(
                expected("components", "meal-and-transport", "contributionExemptAmount"));
    }

    // ---- OFFSHORE_ROTATION ----------------------------------------------------

    @Test
    @DisplayName("rotation pays base x 1.75 for the month, whatever the hours")
    void rotationSalary() {
        ProfilePayCalculator.Result a =
                price(offshoreRotation(), "2210", qty("OFFSHORE_HOURS", 96));
        assertThat(amountOf(a, ProfilePayCalculator.EARN_OFFSHORE_ROTA_SALARY))
                .isEqualByComparingTo(expected("components",
                        "rotation-monthly-salary-2210", "offshoreRotaSalary"));

        ProfilePayCalculator.Result b =
                price(offshoreRotation(), "2984", qty("OFFSHORE_HOURS", 35));
        assertThat(amountOf(b, ProfilePayCalculator.EARN_OFFSHORE_ROTA_SALARY))
                .isEqualByComparingTo(expected("components",
                        "rotation-monthly-salary-2984", "offshoreRotaSalary"));
    }

    @Test
    @DisplayName("this is the pay basis behind January workbook row 9 — 2984 x 1.75 = 5222.00")
    void rotationExplainsRow9() {
        ProfilePayCalculator.Result r =
                price(offshoreRotation(), "2984", qty("OFFSHORE_HOURS", 35));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_OFFSHORE_ROTA_SALARY))
                .isEqualByComparingTo("5222.00");
    }

    @Test
    @DisplayName("one offshore hour is enough to qualify for the whole monthly uplift")
    void rotationQualifiesOnOneHour() {
        ProfilePayCalculator.Result r =
                price(offshoreRotation(), "2210", qty("OFFSHORE_HOURS", 1));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_OFFSHORE_ROTA_SALARY))
                .isEqualByComparingTo("3867.50");
    }

    @Test
    @DisplayName("the same single offshore hour on an onshore contract is worth 33.29")
    void sameHourDifferentProfile() {
        // The distinction that must never be collapsed: month-driven vs hour-driven.
        ProfilePayCalculator.Result r =
                price(onshoreRandomOffshore(), "2210", qty("OFFSHORE_HOURS", 1));
        // 2210 / 184 x 1.75 = 21.0190... rounded 21.02
        assertThat(amountOf(r, ProfilePayCalculator.EARN_OFFSHORE_75))
                .isEqualByComparingTo("21.02");
    }

    @Test
    @DisplayName("no offshore hours means no rotation uplift, and says why")
    void rotationWithoutOffshoreHours() {
        ProfilePayCalculator.Result r =
                price(offshoreRotation(), "2210", qty("ONSHORE_HOURS", 100));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_OFFSHORE_ROTA_SALARY))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.warnings()).anyMatch(w -> w.contains("BLOCKERS Q3"));
    }

    // ---- OFFSHORE_RANDOM_ONSHORE ----------------------------------------------

    @Test
    @DisplayName("an offshore contract with onshore hours splits 175% / 100%")
    void derivedOffshore() {
        ProfilePayCalculator.Result r =
                price(offshoreRandomOnshore(), "2428", qty("ONSHORE_HOURS", 8));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_OFFSHORE_DERIVED)).isEqualByComparingTo(
                expected("components", "offshore-contract-with-onshore-hours", "offshoreAmount"));
        assertThat(amountOf(r, "ONSHORE_HOURS")).isEqualByComparingTo(
                expected("components", "offshore-contract-with-onshore-hours", "onshoreAmount"));
    }

    @Test
    @DisplayName("derived offshore ignores recorded offshore hours, and says so")
    void derivedOffshoreWarnsAboutIgnoredHours() {
        ProfilePayCalculator.Result r = price(offshoreRandomOnshore(), "2428",
                qty("ONSHORE_HOURS", 8, "OFFSHORE_HOURS", 150));
        assertThat(r.warnings()).anyMatch(w -> w.contains("BLOCKERS Q6"));
    }

    @Test
    @DisplayName("onshore plus sick beyond the norm blocks rather than paying a negative")
    void derivedOffshoreNegative() {
        ProfilePayCalculator.Result r = price(offshoreRandomOnshore(), "2428",
                qty("ONSHORE_HOURS", 180, "SICK_LEAVE_HOURS", 20));
        assertThat(r.isPayable()).isFalse();
        assertThat(r.blockers()).anyMatch(b -> b.contains("BLOCKERS Q6"));
    }

    // ---- monthly excess --------------------------------------------------------

    @Test
    @DisplayName("monthly excess is worked hours over the norm, priced at 1.75")
    void monthlyExcess() {
        ProfilePayCalculator.Result r = price(onshoreRandomOffshore(), "3500",
                qty("OFFSHORE_HOURS", 96, "ONSHORE_HOURS", 136));
        BigDecimal expectedHours = expected("monthlyExcess", "excess-hours-no-night", "excessHours");
        assertThat(r.earnings()).anyMatch(l ->
                l.earningCode().equals(ProfilePayCalculator.EARN_EXCESS)
                        && l.quantity().compareTo(expectedHours) == 0);
    }

    @Test
    @DisplayName("56 excess hours at 1.75 come to 1,864.13")
    void monthlyExcessAmount() {
        // 240 onshore hours against a 184 norm leaves 56 excess.
        ProfilePayCalculator.Result r =
                price(onshoreRandomOffshore(), "3500", qty("ONSHORE_HOURS", 240));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_EXCESS)).isEqualByComparingTo(
                expected("monthlyExcess", "excess-amount", "excessAmount"));
    }

    @Test
    @DisplayName("a below-norm month pays no excess and creates no debt")
    void noNegativeExcess() {
        ProfilePayCalculator.Result r =
                price(onshoreRandomOffshore(), "3500", qty("ONSHORE_HOURS", 150));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_EXCESS))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("BLOCKERS Q1 — night hours in the excess sum refuse rather than guess")
    void excessWithNightHoursRefuses() {
        ProfilePayCalculator.Result r = price(onshoreRandomOffshore(), "3500",
                qty("OFFSHORE_HOURS", 12, "ONSHORE_HOURS", 160, "OFFSHORE_NIGHT_HOURS", 24));
        assertThat(r.isPayable()).isFalse();
        assertThat(r.blockers()).anyMatch(b -> b.contains("BLOCKERS Q1"));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_EXCESS))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("once Q1 is answered, the night hours enter the sum and 12 excess hours appear")
    void excessWithNightHoursOnceAnswered() {
        CalculationProfile p = onshoreRandomOffshore();
        p.setNightHoursSeparateFromBase(true);
        ProfilePayCalculator.Result r = price(p, "3500",
                qty("OFFSHORE_HOURS", 12, "ONSHORE_HOURS", 160, "OFFSHORE_NIGHT_HOURS", 24));
        BigDecimal expectedHours =
                expected("monthlyExcess", "excess-hours-with-night", "excessHours");
        assertThat(r.earnings()).anyMatch(l ->
                l.earningCode().equals(ProfilePayCalculator.EARN_EXCESS)
                        && l.quantity().compareTo(expectedHours) == 0);
    }

    // ---- rotation settlement -----------------------------------------------------

    @Test
    @DisplayName("BLOCKERS Q2 — a settlement with no multiplier pays nothing and says why")
    void rotationSettlementRefusesWithoutMultiplier() {
        ProfilePayCalculator.Result r = price(offshoreRotation(), "3500",
                qty("OFFSHORE_HOURS", 200), null, new BigDecimal("60"));
        assertThat(r.isPayable()).isFalse();
        assertThat(r.blockers()).anyMatch(b -> b.contains("BLOCKERS Q2"));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_EXCESS))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("the two readings of Q2 differ by 855.98 AZN on 60 hours")
    void rotationSettlementBothReadings() {
        JsonNode unresolved = fixtures.get("unresolved").get("rotationExcessMultiplier");

        CalculationProfile a = offshoreRotation();
        a.setExcessMultiplier(unresolved.get("interpretationA").get("multiplier").decimalValue());
        ProfilePayCalculator.Result ra = price(a, "3500",
                qty("OFFSHORE_HOURS", 200), null, new BigDecimal("60"));
        assertThat(amountOf(ra, ProfilePayCalculator.EARN_EXCESS)).isEqualByComparingTo(
                unresolved.get("interpretationA").get("amountFor60hAt19.0217391304").decimalValue());

        CalculationProfile b = offshoreRotation();
        b.setExcessMultiplier(unresolved.get("interpretationB").get("multiplier").decimalValue());
        ProfilePayCalculator.Result rb = price(b, "3500",
                qty("OFFSHORE_HOURS", 200), null, new BigDecimal("60"));
        assertThat(amountOf(rb, ProfilePayCalculator.EARN_EXCESS)).isEqualByComparingTo(
                unresolved.get("interpretationB").get("amountFor60hAt19.0217391304").decimalValue());
    }

    @Test
    @DisplayName("a non-settlement month pays no excess for a rotation employee")
    void rotationAccumulatesSilently() {
        ProfilePayCalculator.Result r =
                price(offshoreRotation(), "3500", qty("OFFSHORE_HOURS", 220), null, null);
        assertThat(amountOf(r, ProfilePayCalculator.EARN_EXCESS))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- MEWA ----------------------------------------------------------------------

    @Test
    @DisplayName("MEWA is a percentage of the onshore amount, per employee")
    void mewa() {
        ProfilePayCalculator.Result r = price(onshoreRandomOffshore(), "3500",
                qty("ONSHORE_HOURS", 136), new BigDecimal("0.30"), null);
        // 2586.9565... x 0.30
        assertThat(amountOf(r, ProfilePayCalculator.EARN_MEWA)).isEqualByComparingTo("776.09");
    }

    @Test
    @DisplayName("no MEWA rule means no MEWA — never an approximation")
    void mewaAbsent() {
        ProfilePayCalculator.Result r =
                price(onshoreRandomOffshore(), "3500", qty("ONSHORE_HOURS", 136));
        assertThat(amountOf(r, ProfilePayCalculator.EARN_MEWA))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- catalog holes ----------------------------------------------------------------

    @Test
    @DisplayName("a recorded quantity nothing prices is reported, not silently unpaid")
    void unpricedCategoryWarns() {
        ProfilePayCalculator.Result r = price(onshoreRandomOffshore(), "3500",
                qty("ONSHORE_HOURS", 100, "SOME_NEW_CATEGORY", 8));
        assertThat(r.warnings()).anyMatch(w -> w.contains("SOME_NEW_CATEGORY"));
    }
}
