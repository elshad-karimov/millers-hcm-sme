package az.millers.hcm.payroll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.payroll.domain.StatutoryRule;
import az.millers.hcm.payroll.repo.StatutoryRuleRepository;
import az.millers.hcm.payroll.service.StatutoryCalculator.ContributionPair;
import az.millers.hcm.payroll.service.StatutoryCalculator.DailyOt;
import az.millers.hcm.payroll.service.StatutoryCalculator.IncomeTaxResult;
import az.millers.hcm.payroll.service.StatutoryCalculator.OvertimePay;

/**
 * Payroll-math regression pack — pins the AZ 2026 statutory calculations
 * ({@link StatutoryCalculator}) against known expected values. Pure JUnit (no
 * Spring context, no DB): the statutory rules are stubbed to the exact JSON the
 * production seed carries, so this runs in {@code mvn test} and guards every
 * change against a silent wrong-number payroll bug.
 *
 * <p>The rule JSON below mirrors V16/V35 <b>as corrected by V306</b> — the 2026
 * private-sector income tax has 3% / 10% / 14% brackets and <b>no</b> 200 AZN
 * income-tax exemption (that 200 threshold is DSMF-only). Keep these constants
 * in lock-step with the migrations; a divergence here means a real bug there.
 * Sources: Mercans AZ 2026 statutory alert; PwC / TaxRavens 2026 worked examples.
 */
class StatutoryCalculatorTest {

    // Income tax (V306): 3% ≤2500, 75+10% to 8000, 625+14% above — no exemption.
    private static final String INCOME_TAX_AZ_2026 = """
        {"type":"PROGRESSIVE_BRACKETS","brackets":[
            {"upTo":2500,"rate":0.03,"base":0},
            {"upTo":8000,"rate":0.10,"base":75},
            {"upTo":null,"rate":0.14,"base":625}
        ]}""";

    // DSMF: employee 3% first 200 + 10% to 8000 + 10% above; employer 22/15/11.
    private static final String DSMF_AZ_2026 = """
        {"type":"DSMF_AZ_2026",
          "lowerBound":200,
          "thresholdAbove":8000,
          "employee":{"belowFirst":0.03,"betweenFirstAndThreshold":0.10,"aboveThreshold":0.10},
          "employer":{"belowFirst":0.22,"betweenFirstAndThreshold":0.15,"aboveThreshold":0.11}}""";

    // MMI: 2% each side on the first 2500, 0.5% each side above.
    private static final String MMI_AZ_2026 = """
        {"type":"BANDED_PCT",
          "bands":[
            {"upTo":2500,"employee":0.02,"employer":0.02},
            {"upTo":null,"employee":0.005,"employer":0.005}
        ]}""";

    // Unemployment: flat 0.5% each side.
    private static final String UNEMPLOYMENT_AZ_2026 = """
        {"type":"FLAT_PCT","employee":0.005,"employer":0.005}""";

    // Overtime: 1.5× first 2h/day, 2× after; 2× on holidays/weekends (V35).
    private static final String OVERTIME_AZ_2026 = """
        {"type":"OT_MULTIPLIERS","firstHoursPerDay":2,"firstMultiplier":1.5,"afterMultiplier":2.0,
          "expectedMonthlyHours":160,"holidayMultiplier":2.0}""";

    private static final LocalDate D = LocalDate.of(2026, 1, 1);

    private StatutoryCalculator calculator;

    @BeforeEach
    void setUp() {
        StatutoryRuleRepository mockRepo = mock(StatutoryRuleRepository.class);
        calculator = new StatutoryCalculator(mockRepo, new ObjectMapper());
        stub(mockRepo, "INCOME_TAX_AZ", INCOME_TAX_AZ_2026);
        stub(mockRepo, "DSMF_AZ", DSMF_AZ_2026);
        stub(mockRepo, "MMI_AZ", MMI_AZ_2026);
        stub(mockRepo, "UNEMPLOYMENT_AZ", UNEMPLOYMENT_AZ_2026);
        stub(mockRepo, "OVERTIME_AZ", OVERTIME_AZ_2026);
    }

    private void stub(StatutoryRuleRepository repo, String code, String json) {
        when(repo.findActive(eq(code), eq("AZ"), any(LocalDate.class)))
                .thenReturn(Optional.of(ruleOf(code, json)));
    }

    private StatutoryRule ruleOf(String code, String json) {
        StatutoryRule rule = new StatutoryRule();
        rule.setCode(code);
        rule.setJurisdiction("AZ");
        rule.setRuleJson(json);
        rule.setActive(true);
        rule.setEffectiveFrom(D);
        return rule;
    }

    private BigDecimal tax(String gross) {
        return calculator.incomeTax(new BigDecimal(gross), "AZ", D).tax();
    }

    // ── Income tax — 3% / 10% / 14%, no exemption (V306) ────────────────────

    @Test
    void incomeTax_firstBand_appliesFullAmountAt3pct() {
        // M349-S1 taxable gross 2050 → 2050 × 3% = 61.50 (no 200 exemption).
        assertThat(tax("2050.00")).isEqualByComparingTo("61.50");
    }

    @Test
    void incomeTax_lowEarnerIsTaxed_noExemption() {
        // The 2026 income tax has no non-taxable minimum; a 150 earner pays 3%.
        assertThat(tax("150.00")).isEqualByComparingTo("4.50");
        assertThat(tax("200.00")).isEqualByComparingTo("6.00");
    }

    @Test
    void incomeTax_continuousAtBandBoundary_2500() {
        // No discontinuity at 2500: 2500 × 3% = 75.00 == band-2 base.
        assertThat(tax("2500.00")).isEqualByComparingTo("75.00");
    }

    @Test
    void incomeTax_secondBand_2500to8000() {
        // 5000 → 75 + (5000 − 2500) × 10% = 75 + 250 = 325.00
        assertThat(tax("5000.00")).isEqualByComparingTo("325.00");
    }

    @Test
    void incomeTax_thirdBand_above8000() {
        // 10000 → 625 + (10000 − 8000) × 14% = 625 + 280 = 905.00
        assertThat(tax("10000.00")).isEqualByComparingTo("905.00");
    }

    @Test
    void incomeTax_roundsHalfUpToTwoDp() {
        // 2333.33 × 3% = 69.9999 → HALF_UP → 70.00
        assertThat(tax("2333.33")).isEqualByComparingTo("70.00");
    }

    // ── DSMF — tiered social insurance (200 / 8000 split) ───────────────────

    @Test
    void dsmf_base2200() {
        // 200 × 3% + 2000 × 10% = 6.00 + 200.00 = 206.00
        ContributionPair r = calculator.dsmf(new BigDecimal("2200.00"), "AZ", D);
        assertThat(r.employee()).isEqualByComparingTo("206.00");
    }

    @Test
    void dsmf_crossesUpperThreshold_8000() {
        // 9000: employee 200×3% + 7800×10% + 1000×10% = 6 + 780 + 100 = 886.00
        //       employer 200×22% + 7800×15% + 1000×11% = 44 + 1170 + 110 = 1324.00
        ContributionPair r = calculator.dsmf(new BigDecimal("9000.00"), "AZ", D);
        assertThat(r.employee()).isEqualByComparingTo("886.00");
        assertThat(r.employer()).isEqualByComparingTo("1324.00");
    }

    @Test
    void dsmf_belowLowerBound_200() {
        // 100: employee 100×3% = 3.00; employer 100×22% = 22.00
        ContributionPair r = calculator.dsmf(new BigDecimal("100.00"), "AZ", D);
        assertThat(r.employee()).isEqualByComparingTo("3.00");
        assertThat(r.employer()).isEqualByComparingTo("22.00");
    }

    @Test
    void dsmf_roundsHalfUp() {
        // 2333: 200×3% + 2133×10% = 6.00 + 213.30 = 219.30
        ContributionPair r = calculator.dsmf(new BigDecimal("2333.00"), "AZ", D);
        assertThat(r.employee()).isEqualByComparingTo("219.30");
    }

    // ── MMI — banded medical insurance (2% ≤2500, 0.5% above) ───────────────

    @Test
    void mmi_withinFirstBand() {
        ContributionPair r = calculator.mmi(new BigDecimal("2200.00"), "AZ", D);
        assertThat(r.employee()).isEqualByComparingTo("44.00");   // 2200 × 2%
        assertThat(r.employer()).isEqualByComparingTo("44.00");
    }

    @Test
    void mmi_crossesBand_2500() {
        // 3000: 2500×2% + 500×0.5% = 50.00 + 2.50 = 52.50
        ContributionPair r = calculator.mmi(new BigDecimal("3000.00"), "AZ", D);
        assertThat(r.employee()).isEqualByComparingTo("52.50");
        assertThat(r.employer()).isEqualByComparingTo("52.50");
    }

    // ── Unemployment — flat 0.5% each side ──────────────────────────────────

    @Test
    void unemployment_flatHalfPercent() {
        ContributionPair r = calculator.unemployment(new BigDecimal("2200.00"), "AZ", D);
        assertThat(r.employee()).isEqualByComparingTo("11.00");   // 2200 × 0.5%
        assertThat(r.employer()).isEqualByComparingTo("11.00");
    }

    @Test
    void unemployment_roundsHalfUp() {
        // 1234.56 × 0.5% = 6.1728 → HALF_UP → 6.17
        ContributionPair r = calculator.unemployment(new BigDecimal("1234.56"), "AZ", D);
        assertThat(r.employee()).isEqualByComparingTo("6.17");
    }

    // ── Overtime — 1.5× first 2h, 2× after; 2× holiday/weekend ───────────────

    @Test
    void overtime_standardDay() {
        // salary 2000 / 160h = 12.50/h; 4h → 2×1.5×12.5 + 2×2×12.5 = 37.50 + 50 = 87.50
        OvertimePay r = calculator.overtimePay(new BigDecimal("2000.00"),
                List.of(new DailyOt(new BigDecimal("4.0"), false, false)), "AZ", D);
        assertThat(r.hourlyRate()).isEqualByComparingTo("12.5000");
        assertThat(r.totalPay()).isEqualByComparingTo("87.50");
        assertThat(r.holidayPay()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void overtime_holidayDayAllAtDoubleRate() {
        // 5h holiday → 5 × 2.0 × 12.50 = 125.00
        OvertimePay r = calculator.overtimePay(new BigDecimal("2000.00"),
                List.of(new DailyOt(new BigDecimal("5.0"), true, false)), "AZ", D);
        assertThat(r.totalPay()).isEqualByComparingTo("125.00");
        assertThat(r.holidayHours()).isEqualByComparingTo("5.0");
        assertThat(r.holidayPay()).isEqualByComparingTo("125.00");
    }

    @Test
    void overtime_weekendDayAllAtDoubleRate() {
        // 3h weekend → 3 × 2.0 × 12.50 = 75.00
        OvertimePay r = calculator.overtimePay(new BigDecimal("2000.00"),
                List.of(new DailyOt(new BigDecimal("3.0"), false, true)), "AZ", D);
        assertThat(r.totalPay()).isEqualByComparingTo("75.00");
        assertThat(r.weekendPay()).isEqualByComparingTo("75.00");
        assertThat(r.holidayHours()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void overtime_mixedStandardAndHoliday() {
        // 2h standard (2×1.5×12.5 = 37.50) + 3h holiday (3×2×12.5 = 75.00) = 112.50
        OvertimePay r = calculator.overtimePay(new BigDecimal("2000.00"),
                List.of(new DailyOt(new BigDecimal("2.0"), false, false),
                        new DailyOt(new BigDecimal("3.0"), true, false)), "AZ", D);
        assertThat(r.totalPay()).isEqualByComparingTo("112.50");
        assertThat(r.holidayPay()).isEqualByComparingTo("75.00");
    }

    // ── Full worked example — M349-S1 end to end (corrected) ─────────────────

    @Test
    void fullWorkedExample_M349S1() {
        // base 2000 + taxable MEAL 50 + non-taxable-but-contributory TRANSPORT 150.
        //   taxable gross = 2050;  contribution base = 2200.
        BigDecimal taxableGross = new BigDecimal("2050.00");
        BigDecimal contribGross = new BigDecimal("2200.00");

        BigDecimal incomeTax = calculator.incomeTax(taxableGross, "AZ", D).tax();
        BigDecimal dsmf = calculator.dsmf(contribGross, "AZ", D).employee();
        BigDecimal mmi = calculator.mmi(contribGross, "AZ", D).employee();
        BigDecimal unemp = calculator.unemployment(contribGross, "AZ", D).employee();

        assertThat(incomeTax).isEqualByComparingTo("61.50");
        assertThat(dsmf).isEqualByComparingTo("206.00");
        assertThat(mmi).isEqualByComparingTo("44.00");
        assertThat(unemp).isEqualByComparingTo("11.00");

        BigDecimal deductions = incomeTax.add(dsmf).add(mmi).add(unemp);
        assertThat(deductions).isEqualByComparingTo("322.50");

        // Net = taxable gross − statutory deductions + non-taxable TRANSPORT added back.
        BigDecimal net = taxableGross.subtract(deductions).add(new BigDecimal("150.00"));
        assertThat(net).isEqualByComparingTo("1877.50");
    }
}
