package az.millers.hcm.payroll.timepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The blocking payroll-validation gate for slice 3.
 *
 * <p>Every expected value below comes from the customer's own workbook
 * ("Copy of Payroll calculation 2026 January 2.xlsm", sheet "For JX"), captured
 * into {@code fixtures/january-2026.json}. The point is not that the code runs
 * — it is that it produces the same money the business already pays, to the
 * cent. A payroll error is silent and expensive, so nothing here is asserted
 * approximately.
 *
 * <p>Three of the seven rows are <em>deliberately</em> asserted as mismatching:
 * their workbook formulas contradict the stated rules (hardcoded hour counts, a
 * different pay basis). Pinning the difference is the honest outcome — quietly
 * reproducing a hand-typed override would bake one person's typo into an engine.
 */
class TimesheetPayCalculatorTest {

    private static JsonNode fixtures;
    private static BigDecimal normHours;
    private final TimesheetPayCalculator calculator = new TimesheetPayCalculator();
    private static final LocalDate PERIOD = LocalDate.of(2026, 1, 1);

    @BeforeAll
    static void loadFixtures() throws Exception {
        try (InputStream in = TimesheetPayCalculatorTest.class
                .getResourceAsStream("/fixtures/january-2026.json")) {
            fixtures = new ObjectMapper().readTree(in);
        }
        normHours = fixtures.get("normWorkingHours").decimalValue();
    }

    // ---- the rule catalog, as seeded by V319 -------------------------------

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
        return r;
    }

    private static List<TimePayRule> seededRules() {
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

    private static EmployeeExcessRule percentOfOnshore(String percentage) {
        EmployeeExcessRule r = new EmployeeExcessRule();
        r.setEmployeeId(UUID.randomUUID());
        r.setMethod("PERCENT_OF_ONSHORE");
        r.setPercentage(new BigDecimal(percentage));
        r.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return r;
    }

    private static EmployeeExcessRule unitsAtRate(String units, String multiplier) {
        EmployeeExcessRule r = new EmployeeExcessRule();
        r.setEmployeeId(UUID.randomUUID());
        r.setMethod("UNITS_AT_RATE");
        r.setUnits(new BigDecimal(units));
        r.setMultiplier(new BigDecimal(multiplier));
        r.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return r;
    }

    // ---- fixture plumbing --------------------------------------------------

    private JsonNode row(int rowNumber) {
        for (JsonNode e : fixtures.get("employees")) {
            if (e.get("row").asInt() == rowNumber) return e;
        }
        throw new IllegalArgumentException("No fixture row " + rowNumber);
    }

    private TimesheetPayCalculator.Result calc(int rowNumber, EmployeeExcessRule excess) {
        JsonNode e = row(rowNumber);
        Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        e.get("quantities").fields()
                .forEachRemaining(f -> quantities.put(f.getKey(), f.getValue().decimalValue()));
        JsonNode exp = e.get("expected");

        return calculator.calculate(new TimesheetPayCalculator.Input(
                e.get("baseSalary").decimalValue(), normHours, PERIOD,
                quantities, seededRules(), excess,
                exp.get("extraAmount").decimalValue(),
                exp.get("vacationAmount").decimalValue(),
                exp.get("sickLeaveAmount").decimalValue(),
                exp.get("lifeInsurance").decimalValue(),
                exp.get("azercell").decimalValue(),
                exp.get("advance").decimalValue()));
    }

    private BigDecimal expected(int rowNumber, String field) {
        return row(rowNumber).get("expected").get(field).decimalValue();
    }

    // =======================================================================
    //  Rows the workbook computes with the canonical formulas throughout.
    //  These must match to the cent.
    // =======================================================================

    @Test
    void row13_dataProcessor_matchesTheWorkbookExactly() {
        var r = calc(13, null);

        assertThat(r.hourlyRate()).isEqualByComparingTo(expected(13, "hourlyRate"));
        assertThat(r.overtimeRate()).isEqualByComparingTo(expected(13, "overtimeRate"));
        assertThat(r.gross()).isEqualByComparingTo(expected(13, "gross"));
        assertThat(r.incomeTax()).isEqualByComparingTo(expected(13, "incomeTax"));
        assertThat(r.spf()).isEqualByComparingTo(expected(13, "spf"));
        assertThat(r.unemploymentFund()).isEqualByComparingTo(expected(13, "unemploymentFund"));
        assertThat(r.compulsoryInsurance()).isEqualByComparingTo(expected(13, "compulsoryInsurance"));
        assertThat(r.totalDeductions()).isEqualByComparingTo(expected(13, "totalDeductions"));
        assertThat(r.netPay()).isEqualByComparingTo(expected(13, "netPay"));
    }

    @Test
    void row14_hseTrainingOfficer_withOvertime_matchesTheWorkbookExactly() {
        var r = calc(14, null);

        assertThat(r.gross()).isEqualByComparingTo(expected(14, "gross"));
        assertThat(r.incomeTax()).isEqualByComparingTo(expected(14, "incomeTax"));
        assertThat(r.spf()).isEqualByComparingTo(expected(14, "spf"));
        assertThat(r.unemploymentFund()).isEqualByComparingTo(expected(14, "unemploymentFund"));
        assertThat(r.compulsoryInsurance()).isEqualByComparingTo(expected(14, "compulsoryInsurance"));
        assertThat(r.netPay()).isEqualByComparingTo(expected(14, "netPay"));
    }

    // =======================================================================
    //  Rows that match once their excess rule is configured.
    // =======================================================================

    @Test
    void row10_seniorAccountant_matchesOnceIts30PercentExcessRuleIsConfigured() {
        var r = calc(10, percentOfOnshore("0.30"));

        assertThat(r.gross()).isEqualByComparingTo(expected(10, "gross"));
        assertThat(r.netPay()).isEqualByComparingTo(expected(10, "netPay"));
    }

    @Test
    void row11_transportOfficer_matchesOnceIts60PercentExcessRuleIsConfigured() {
        var r = calc(11, percentOfOnshore("0.60"));

        assertThat(r.gross()).isEqualByComparingTo(expected(11, "gross"));
        assertThat(r.netPay()).isEqualByComparingTo(expected(11, "netPay"));
    }

    @Test
    void withoutAnExcessRuleTheExcessAmountIsZero_notAGuess() {
        var withRule = calc(10, percentOfOnshore("0.30"));
        var withoutRule = calc(10, null);

        assertThat(withoutRule.gross())
                .isLessThan(withRule.gross())
                .isEqualByComparingTo(expected(10, "gross").subtract(expected(10, "excessAmount")));
    }

    // =======================================================================
    //  Rows whose workbook formulas contradict the stated rules.
    //  Asserted as MISMATCHING on purpose — see PRD §3.2.
    // =======================================================================

    @Test
    void row8_hvacTechnician_deviates_becauseTheWorkbookHardcodes136HoursInsteadOf132() {
        // Everything except the offshore line agrees; the workbook's col T is
        // =Q8/151*136*1.75 while the declared offshore quantity (C8) is 132.
        var r = calc(8, unitsAtRate("33", "1.6"));

        BigDecimal difference = expected(8, "gross").subtract(r.gross());
        assertThat(r.gross()).isNotEqualByComparingTo(expected(8, "gross"));
        // The gap is exactly the four phantom hours and nothing else:
        // 4 x (2210 / 151) x 1.75 = 102.45. Everything else on this row agrees,
        // which is what makes it a typo rather than a different pay rule.
        assertThat(difference).isEqualByComparingTo(new BigDecimal("102.45"));
    }

    @Test
    void row9_vesselFieldEngineer_deviates_becauseTheWorkbookPaysSalaryTimes175IgnoringHours() {
        // Col T is =Q9*1.75 — monthly salary, no hours, no rate. The canonical
        // rule on 35 declared offshore hours pays a fraction of that.
        var r = calc(9, null);

        BigDecimal difference = expected(9, "gross").subtract(r.gross());
        assertThat(difference).isGreaterThan(new BigDecimal("4000"));
    }

    @Test
    void row12_seniorElectrician_deviates_becauseTheWorkbookHardcodesBothHourCounts() {
        // Col T uses 80 offshore hours against a declared 121; col U uses 168
        // quayside hours against a declared 154.
        var r = calc(12, unitsAtRate("85", "1.6"));

        assertThat(r.gross()).isNotEqualByComparingTo(expected(12, "gross"));
    }

    // =======================================================================
    //  Behaviour that protects the numbers.
    // =======================================================================

    @Test
    void baseSalaryIsNotPaid_onlyRecordedWorkIs() {
        var r = calculator.calculate(new TimesheetPayCalculator.Input(
                new BigDecimal("2500"), normHours, PERIOD,
                Map.of(), seededRules(), null,
                null, null, null, null, null, null));

        assertThat(r.gross()).isEqualByComparingTo("0.00");
        assertThat(r.netPay()).isEqualByComparingTo("0.00");
    }

    @Test
    void mealAllowanceIsPaidInFullButFiveAznPerDayIsExemptFromEveryContributionBase() {
        var r = calc(13, null);   // 19 meal days

        assertThat(r.contributionExemptAmount()).isEqualByComparingTo("95.00");   // 19 x 5
        // Paid at 12/day even though only 5/day is exempt.
        assertThat(r.earnings())
                .filteredOn(l -> "MEAL_ALLOWANCE_DAYS".equals(l.categoryCode()))
                .singleElement()
                .satisfies(l -> assertThat(l.amount()).isEqualByComparingTo("228.00"));
    }

    @Test
    void grossBelowTwoHundredIsNotTaxed() {
        var r = calculator.calculate(new TimesheetPayCalculator.Input(
                new BigDecimal("1510"), normHours, PERIOD,
                Map.of("ONSHORE_HOURS", new BigDecimal("10")),
                seededRules(), null, null, null, null, null, null, null));

        assertThat(r.gross()).isEqualByComparingTo("100.00");
        assertThat(r.incomeTax()).isEqualByComparingTo("0.00");
    }

    @Test
    void recordedQuantitiesWithNoPayRuleAreReportedRatherThanSilentlyUnpaid() {
        Map<String, BigDecimal> q = new LinkedHashMap<>();
        q.put("ONSHORE_HOURS", new BigDecimal("151"));
        q.put("SOME_NEW_CATEGORY", new BigDecimal("8"));

        var r = calculator.calculate(new TimesheetPayCalculator.Input(
                new BigDecimal("1875"), normHours, PERIOD, q,
                seededRules(), null, null, null, null, null, null, null));

        assertThat(r.warnings()).anySatisfy(w ->
                assertThat(w).contains("SOME_NEW_CATEGORY").contains("not being paid"));
    }

    @Test
    void excessHoursWithoutAConfiguredRuleWarnsRatherThanGuessing() {
        Map<String, BigDecimal> q = new LinkedHashMap<>();
        q.put("ONSHORE_HOURS", new BigDecimal("151"));
        q.put("EXCESS_HOURS", new BigDecimal("33"));

        var r = calculator.calculate(new TimesheetPayCalculator.Input(
                new BigDecimal("1875"), normHours, PERIOD, q,
                seededRules(), null, null, null, null, null, null, null));

        assertThat(r.warnings()).anySatisfy(w -> assertThat(w).contains("no excess rule"));
    }

    @Test
    void missingNormHoursIsRefusedRatherThanDividedByZero() {
        assertThatThrownBy(() -> calculator.calculate(new TimesheetPayCalculator.Input(
                new BigDecimal("1875"), BigDecimal.ZERO, PERIOD, Map.of(),
                seededRules(), null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Norm working hours");
    }

    // =======================================================================
    //  The override mechanism — how a deviating row becomes configuration
    //  rather than a code change (PRD §3.2, selected default).
    // =======================================================================

    @Test
    void row9_reproducesExactlyOnceItsOffshoreLineIsOverriddenToMonthlySalaryMultiple() {
        // The workbook pays this employee offshore as salary x 1.75 regardless
        // of hours. Expressed as a dated per-employee override, the canonical
        // engine reproduces the workbook to the cent — proving the deviation is
        // a pay basis the system can hold, not something it has to hardcode.
        TimePayRuleOverride o = new TimePayRuleOverride();
        o.setEmployeeId(UUID.randomUUID());
        o.setCategoryCode("OFFSHORE_HOURS");
        o.setBasis("MONTHLY_SALARY_MULTIPLE");
        o.setMultiplier(new BigDecimal("1.75"));
        o.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        o.setReason("Rota retainer basis per contract");

        List<TimePayRule> rules = new ArrayList<>();
        for (TimePayRule r : seededRules()) {
            rules.add("OFFSHORE_HOURS".equals(r.getCategoryCode()) ? o.applyTo(r) : r);
        }

        JsonNode e = row(9);
        Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        e.get("quantities").fields()
                .forEachRemaining(f -> quantities.put(f.getKey(), f.getValue().decimalValue()));

        var r = calculator.calculate(new TimesheetPayCalculator.Input(
                e.get("baseSalary").decimalValue(), normHours, PERIOD,
                quantities, rules, null, null, null, null, null, null, null));

        assertThat(r.gross()).isEqualByComparingTo(expected(9, "gross"));
        assertThat(r.incomeTax()).isEqualByComparingTo(expected(9, "incomeTax"));
        assertThat(r.netPay()).isEqualByComparingTo(expected(9, "netPay"));
    }

    @Test
    void anOverrideOutsideItsEffectiveWindowDoesNotApply() {
        TimePayRuleOverride o = new TimePayRuleOverride();
        o.setEmployeeId(UUID.randomUUID());
        o.setCategoryCode("OFFSHORE_HOURS");
        o.setBasis("MONTHLY_SALARY_MULTIPLE");
        o.setMultiplier(new BigDecimal("1.75"));
        o.setEffectiveFrom(LocalDate.of(2026, 6, 1));   // starts after this period
        o.setReason("Future change");

        assertThat(o.coversPeriodStart(PERIOD)).isFalse();
    }

    @Test
    void everyFixtureRowProducesANonNegativeNetPay() {
        List<Integer> rows = new ArrayList<>();
        fixtures.get("employees").forEach(e -> rows.add(e.get("row").asInt()));

        for (int row : rows) {
            var r = calc(row, null);
            assertThat(r.netPay())
                    .as("net pay for fixture row %d", row)
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }
}
