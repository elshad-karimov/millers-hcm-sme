package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.payroll.domain.StatutoryRule;
import az.millers.hcm.payroll.repo.StatutoryRuleRepository;

/**
 * Reads {@code statutory_rule} rows by code + jurisdiction + effective date
 * and applies their configured JSON formulas. Supported formula types:
 *
 * <ul>
 *   <li>{@code PROGRESSIVE_BRACKETS} — income tax with an optional first-bracket
 *       exemption that only applies below a threshold (AZ 2026 quirk).</li>
 *   <li>{@code DSMF_AZ_2026} — split tiered contribution, employee + employer.</li>
 *   <li>{@code BANDED_PCT} — banded percentage, employee + employer.</li>
 *   <li>{@code FLAT_PCT} — flat percentage, employee + employer.</li>
 *   <li>{@code OT_MULTIPLIERS} — per-day overtime multipliers.</li>
 * </ul>
 */
@Service
public class StatutoryCalculator {

    private static final BigDecimal TWO_HUNDRED = BigDecimal.valueOf(200);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final StatutoryRuleRepository rules;
    private final ObjectMapper objectMapper;

    public StatutoryCalculator(StatutoryRuleRepository rules, ObjectMapper objectMapper) {
        this.rules = rules;
        this.objectMapper = objectMapper;
    }

    private JsonNode loadRule(String code, String jurisdiction, LocalDate date) {
        StatutoryRule rule = rules.findActive(code, jurisdiction, date)
                .orElseThrow(() -> new BadRequestException(
                        "No active rule for " + code + " / " + jurisdiction + " on " + date));
        try {
            return objectMapper.readTree(rule.getRuleJson());
        } catch (Exception ex) {
            throw new BadRequestException("Failed to parse rule JSON for " + code + ": " + ex.getMessage());
        }
    }

    // ---------- Income tax (PROGRESSIVE_BRACKETS) ----------

    public IncomeTaxResult incomeTax(BigDecimal gross, String jurisdiction, LocalDate date) {
        JsonNode rule = loadRule("INCOME_TAX_" + jurisdiction, jurisdiction, date);
        JsonNode brackets = rule.path("brackets");
        if (!brackets.isArray()) throw new BadRequestException("Income tax brackets missing");

        Map<String, Object> trace = new HashMap<>();
        trace.put("gross", gross.toPlainString());

        // Walk brackets, tracking the running floor (the previous bracket's upTo).
        // First match wins. The "exemption" on the first bracket only applies when
        // gross falls within that bracket — the AZ first-band quirk is encoded by
        // simply not propagating the exemption to upper bands.
        BigDecimal floor = BigDecimal.ZERO;
        for (JsonNode b : brackets) {
            BigDecimal upTo = b.hasNonNull("upTo")
                    ? new BigDecimal(b.get("upTo").asText())
                    : null;
            BigDecimal rate = new BigDecimal(b.get("rate").asText());
            BigDecimal base = b.has("base") ? new BigDecimal(b.get("base").asText()) : BigDecimal.ZERO;
            BigDecimal exemption = b.hasNonNull("exemption")
                    ? new BigDecimal(b.get("exemption").asText())
                    : BigDecimal.ZERO;

            boolean fitsInBand = upTo == null || gross.compareTo(upTo) <= 0;
            if (!fitsInBand) {
                floor = upTo;
                continue;
            }

            BigDecimal taxable = gross.subtract(floor).subtract(exemption).max(BigDecimal.ZERO);
            BigDecimal tax = base.add(taxable.multiply(rate)).setScale(2, RoundingMode.HALF_UP);

            trace.put("band", upTo == null
                    ? "> " + floor + " AZN"
                    : (floor.signum() == 0 ? "≤ " + upTo + " AZN" : floor + "–" + upTo + " AZN"));
            trace.put("floor", floor.toPlainString());
            trace.put("exemption", exemption.toPlainString());
            trace.put("taxable", taxable.toPlainString());
            trace.put("rate", rate.toPlainString());
            trace.put("base", base.toPlainString());
            trace.put("tax", tax.toPlainString());
            return new IncomeTaxResult(tax, trace);
        }
        throw new BadRequestException("Income tax: no matching bracket for " + gross);
    }

    // ---------- DSMF (DSMF_AZ_2026) ----------

    public ContributionPair dsmf(BigDecimal gross, String jurisdiction, LocalDate date) {
        JsonNode rule = loadRule("DSMF_" + jurisdiction, jurisdiction, date);
        BigDecimal lower = new BigDecimal(rule.path("lowerBound").asText("200"));
        BigDecimal threshold = new BigDecimal(rule.path("thresholdAbove").asText("8000"));

        BigDecimal employee = tieredDsmf(gross, lower, threshold,
                rule.path("employee"));
        BigDecimal employer = tieredDsmf(gross, lower, threshold,
                rule.path("employer"));
        return new ContributionPair(employee, employer);
    }

    private BigDecimal tieredDsmf(BigDecimal gross, BigDecimal lower, BigDecimal threshold, JsonNode side) {
        BigDecimal belowFirst = new BigDecimal(side.path("belowFirst").asText("0"));
        BigDecimal between = new BigDecimal(side.path("betweenFirstAndThreshold").asText("0"));
        BigDecimal aboveThreshold = new BigDecimal(side.path("aboveThreshold").asText("0"));

        BigDecimal first = gross.min(lower);
        BigDecimal middle = gross.min(threshold).subtract(lower).max(BigDecimal.ZERO);
        BigDecimal extra = gross.subtract(threshold).max(BigDecimal.ZERO);

        return first.multiply(belowFirst)
                .add(middle.multiply(between))
                .add(extra.multiply(aboveThreshold))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ---------- MMI (BANDED_PCT) ----------

    public ContributionPair mmi(BigDecimal gross, String jurisdiction, LocalDate date) {
        JsonNode rule = loadRule("MMI_" + jurisdiction, jurisdiction, date);
        return banded(gross, rule.path("bands"));
    }

    private ContributionPair banded(BigDecimal gross, JsonNode bands) {
        if (!bands.isArray()) throw new BadRequestException("Bands missing");
        BigDecimal employee = BigDecimal.ZERO;
        BigDecimal employer = BigDecimal.ZERO;
        BigDecimal floor = BigDecimal.ZERO;
        for (JsonNode b : bands) {
            BigDecimal upTo = b.hasNonNull("upTo")
                    ? new BigDecimal(b.get("upTo").asText())
                    : null;
            BigDecimal portion = (upTo == null ? gross : gross.min(upTo)).subtract(floor).max(BigDecimal.ZERO);
            BigDecimal empRate = new BigDecimal(b.path("employee").asText("0"));
            BigDecimal erRate = new BigDecimal(b.path("employer").asText("0"));
            employee = employee.add(portion.multiply(empRate));
            employer = employer.add(portion.multiply(erRate));
            if (upTo == null) break;
            floor = upTo;
        }
        return new ContributionPair(
                employee.setScale(2, RoundingMode.HALF_UP),
                employer.setScale(2, RoundingMode.HALF_UP));
    }

    // ---------- Unemployment (FLAT_PCT) ----------

    public ContributionPair unemployment(BigDecimal gross, String jurisdiction, LocalDate date) {
        JsonNode rule = loadRule("UNEMPLOYMENT_" + jurisdiction, jurisdiction, date);
        BigDecimal empRate = new BigDecimal(rule.path("employee").asText("0"));
        BigDecimal erRate = new BigDecimal(rule.path("employer").asText("0"));
        return new ContributionPair(
                gross.multiply(empRate).setScale(2, RoundingMode.HALF_UP),
                gross.multiply(erRate).setScale(2, RoundingMode.HALF_UP));
    }

    // ---------- Overtime (OT_MULTIPLIERS) ----------

    /**
     * Daily OT hours from the timesheet.
     *
     * <p>Day classification (first match wins):
     * <ol>
     *   <li><b>Holiday OT</b> (M39 / Art. 167) — {@link DailyOt#holiday()} true:
     *       ALL hours paid at {@code holidayMultiplier} (default 2.0).
     *       The 1.5×/2× split does NOT apply.</li>
     *   <li><b>Weekend OT</b> (M45 / Art. 167) — {@link DailyOt#weekend()} true
     *       (and not also a holiday): ALL hours paid at
     *       {@code weekendMultiplier} (default 2.0). Same floor as holidays.</li>
     *   <li><b>Standard OT</b> (Art. 165) — first {@code firstHoursPerDay} at
     *       {@code firstMultiplier} (1.5×), the rest at {@code afterMultiplier} (2×).</li>
     * </ol>
     *
     * <p><b>Daily cap (M45 / Art. 99)</b> — when {@code dailyOtCapHours} is set in
     * the rule JSON, each day's hours are silently capped before the multiplier
     * is applied. The rule defaults to 4.0 for AZ per the most common interpretation
     * of "4 hours per 2 consecutive days". Excess hours are tracked as
     * {@link OvertimePay#cappedHours()} for auditor trace.
     */
    public OvertimePay overtimePay(BigDecimal monthlySalary, List<DailyOt> dailyOt,
                                    String jurisdiction, LocalDate date) {
        JsonNode rule = loadRule("OVERTIME_" + jurisdiction, jurisdiction, date);
        BigDecimal firstHours = new BigDecimal(rule.path("firstHoursPerDay").asText("2"));
        BigDecimal firstMul   = new BigDecimal(rule.path("firstMultiplier").asText("1.5"));
        BigDecimal afterMul   = new BigDecimal(rule.path("afterMultiplier").asText("2.0"));
        // M39: back-compat default = afterMul when V35 hasn't run yet.
        BigDecimal holidayMul = new BigDecimal(
                rule.path("holidayMultiplier").asText(afterMul.toPlainString()));
        // M45: weekend premium (Art. 167 also covers weekly rest days).
        //   Back-compat default = afterMul when V38 hasn't run yet.
        BigDecimal weekendMul = new BigDecimal(
                rule.path("weekendMultiplier").asText(afterMul.toPlainString()));
        // M45: per-day cap. null = no cap (behaviour before V38).
        BigDecimal dailyCapHours = rule.hasNonNull("dailyOtCapHours")
                ? new BigDecimal(rule.path("dailyOtCapHours").asText())
                : null;
        BigDecimal expectedMonthlyHours = new BigDecimal(
                rule.path("expectedMonthlyHours").asText("160"));

        if (expectedMonthlyHours.signum() <= 0) {
            throw new BadRequestException("expectedMonthlyHours must be positive in OT rule");
        }
        BigDecimal hourly = monthlySalary.divide(expectedMonthlyHours, 4, RoundingMode.HALF_UP);

        BigDecimal totalOt      = BigDecimal.ZERO;
        BigDecimal pay          = BigDecimal.ZERO;
        BigDecimal holidayHours = BigDecimal.ZERO;
        BigDecimal holidayPay   = BigDecimal.ZERO;
        BigDecimal weekendHours = BigDecimal.ZERO;
        BigDecimal weekendPay   = BigDecimal.ZERO;
        BigDecimal cappedHours  = BigDecimal.ZERO;   // hours dropped by the Art. 99 cap

        for (DailyOt day : dailyOt) {
            BigDecimal hours = day == null ? null : day.hours();
            if (hours == null || hours.signum() <= 0) continue;

            // Art. 99 per-day cap — applied BEFORE the premium.
            BigDecimal billable = hours;
            if (dailyCapHours != null && hours.compareTo(dailyCapHours) > 0) {
                cappedHours = cappedHours.add(hours.subtract(dailyCapHours));
                billable = dailyCapHours;
            }

            if (day.holiday()) {
                // Art. 167 holiday: all billable hours at holidayMul. No 1.5×/2× split.
                BigDecimal dayPay = billable.multiply(holidayMul).multiply(hourly);
                pay         = pay.add(dayPay);
                holidayHours = holidayHours.add(billable);
                holidayPay   = holidayPay.add(dayPay);
            } else if (day.weekend()) {
                // Art. 167 weekend: same premium path, tracked separately.
                BigDecimal dayPay = billable.multiply(weekendMul).multiply(hourly);
                pay         = pay.add(dayPay);
                weekendHours = weekendHours.add(billable);
                weekendPay   = weekendPay.add(dayPay);
            } else {
                // Art. 165 standard: 1.5× for first N hours, 2× beyond.
                BigDecimal first = billable.min(firstHours);
                BigDecimal extra = billable.subtract(firstHours).max(BigDecimal.ZERO);
                pay = pay.add(first.multiply(firstMul).multiply(hourly));
                pay = pay.add(extra.multiply(afterMul).multiply(hourly));
            }
            totalOt = totalOt.add(billable);
        }
        return new OvertimePay(
                totalOt,
                pay.setScale(2, RoundingMode.HALF_UP),
                hourly.setScale(4, RoundingMode.HALF_UP),
                expectedMonthlyHours,
                holidayHours,
                holidayPay.setScale(2, RoundingMode.HALF_UP),
                weekendHours,
                weekendPay.setScale(2, RoundingMode.HALF_UP),
                cappedHours);
    }

    public Optional<JsonNode> ruleData(String code, String jurisdiction, LocalDate date) {
        return rules.findActive(code, jurisdiction, date).map(r -> {
            try { return objectMapper.readTree(r.getRuleJson()); }
            catch (Exception e) { return null; }
        });
    }

    public record IncomeTaxResult(BigDecimal tax, Map<String, Object> trace) {}
    public record ContributionPair(BigDecimal employee, BigDecimal employer) {}

    /**
     * One day of OT input. Classification (first match wins — holiday beats weekend):
     * <ul>
     *   <li>{@code holiday=true} → Art. 167 holiday premium (M39)</li>
     *   <li>{@code weekend=true} → Art. 167 weekend premium (M45)</li>
     *   <li>both false → Art. 165 standard ladder (1.5×/2×)</li>
     * </ul>
     */
    public record DailyOt(BigDecimal hours, boolean holiday, boolean weekend) {
        /** Back-compat constructor for callers that only track holiday flag (pre-M45). */
        public DailyOt(BigDecimal hours, boolean holiday) {
            this(hours, holiday, false);
        }
    }

    public record OvertimePay(
            BigDecimal totalHours,
            BigDecimal totalPay,
            BigDecimal hourlyRate,
            BigDecimal expectedMonthlyHours,
            /** M39: billable hours where the day was a public holiday. */
            BigDecimal holidayHours,
            /** M39: pay attributable to holiday OT (Art. 167). */
            BigDecimal holidayPay,
            /** M45: billable hours where the day was a weekend (not also a holiday). */
            BigDecimal weekendHours,
            /** M45: pay attributable to weekend OT (Art. 167). */
            BigDecimal weekendPay,
            /** M45: hours dropped by the Art. 99 per-day cap (audit trail only). */
            BigDecimal cappedHours) {}
}
