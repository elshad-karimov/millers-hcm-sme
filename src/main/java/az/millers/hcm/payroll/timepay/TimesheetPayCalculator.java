package az.millers.hcm.payroll.timepay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Prices an approved month's timesheet quantities.
 *
 * <p>Transcribed from "Copy of Payroll calculation 2026 January 2.xlsm", sheet
 * "For JX". Every constant here corresponds to a formula in that workbook; none
 * was chosen. Pure arithmetic — no repository, no clock, no I/O — so it can be
 * pinned against the workbook's own expected values in a unit test.
 *
 * <h2>What the workbook actually does</h2>
 * Base salary is <strong>not paid</strong>. It exists only to derive
 * {@code hourlyRate = baseSalary / normHours}; gross is the sum of the category
 * amounts, so an employee with no recorded hours earns nothing.
 *
 * <p>Multipliers are absolute, not premiums: offshore at 1.75 pays 1.75x the
 * hourly rate in total. Nightshift at 0.2 is a top-up on hours already paid
 * through the offshore or quayside line, matching the capture rule that night
 * hours re-classify hours already counted.
 *
 * <h2>Rounding</h2>
 * The workbook keeps full precision throughout and only the display is rounded,
 * so intermediate values are <em>not</em> rounded here either. Only the final
 * figures are, at 2dp HALF_UP. Rounding each line first drifts by cents against
 * the workbook, which is exactly the silent mismatch this slice exists to avoid.
 */
@Component
public class TimesheetPayCalculator {

    /** Contribution-base scale. Money is 2dp; rates keep more. */
    private static final int RATE_SCALE = 10;
    private static final BigDecimal OVERTIME_FACTOR = BigDecimal.valueOf(2);

    /**
     * Stateless, so it is held directly rather than injected — this class stays
     * constructible with {@code new} in a test, which is the point of it being
     * pure arithmetic.
     */
    private final StatutoryDeductionCalculator statutory = new StatutoryDeductionCalculator();

    /** Everything the calculation needs, so nothing is fetched mid-sum. */
    public record Input(
            BigDecimal baseSalary,
            BigDecimal normHours,
            LocalDate periodStart,
            /** category code -> approved month quantity */
            Map<String, BigDecimal> quantities,
            List<TimePayRule> rules,
            EmployeeExcessRule excessRule,
            /** Manual workbook columns AG / AH / AI. */
            BigDecimal extraAmount,
            BigDecimal vacationAmount,
            BigDecimal sickLeaveAmount,
            /** Manual deduction columns AO / AP / AQ. */
            BigDecimal lifeInsurance,
            BigDecimal azercell,
            BigDecimal advance) {
    }

    /** One priced line, so the result can be read next to the workbook column. */
    public record Line(String categoryCode, String label, BigDecimal quantity,
                       BigDecimal rate, BigDecimal amount) {
    }

    public record Result(
            BigDecimal hourlyRate,
            BigDecimal overtimeRate,
            List<Line> earnings,
            BigDecimal gross,
            BigDecimal contributionExemptAmount,
            BigDecimal incomeTax,
            BigDecimal spf,
            BigDecimal unemploymentFund,
            BigDecimal compulsoryInsurance,
            BigDecimal lifeInsurance,
            BigDecimal azercell,
            BigDecimal advance,
            BigDecimal totalDeductions,
            BigDecimal netPay,
            /** Anything the caller must resolve before this is trustworthy. */
            List<String> warnings) {
    }

    public Result calculate(Input in) {
        List<String> warnings = new ArrayList<>();

        BigDecimal norm = in.normHours();
        if (norm == null || norm.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Norm working hours must be configured for the period before pay can be "
                            + "calculated — it is the divisor behind every rate.");
        }
        BigDecimal base = nz(in.baseSalary());
        BigDecimal hourlyRate = base.divide(norm, RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal overtimeRate = hourlyRate.multiply(OVERTIME_FACTOR);

        Map<String, BigDecimal> qty = in.quantities() == null ? Map.of() : in.quantities();
        List<Line> earnings = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal exempt = BigDecimal.ZERO;
        BigDecimal onshoreAmount = BigDecimal.ZERO;

        List<TimePayRule> rules = new ArrayList<>(in.rules() == null ? List.<TimePayRule>of() : in.rules());
        rules.sort((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()));

        for (TimePayRule rule : rules) {
            if (!rule.isActive()) continue;
            BigDecimal quantity = nz(qty.get(rule.getCategoryCode()));
            if (quantity.signum() == 0) continue;

            BigDecimal rate;
            BigDecimal amount;
            if ("MONTHLY_SALARY_MULTIPLE".equals(rule.getBasis())) {
                // Row 9 of the workbook pays offshore as salary x 1.75 regardless
                // of hours. Expressed as a basis rather than hardcoded so the
                // choice stays configuration: the quantity gates it (no recorded
                // hours, no payment) but does not scale it.
                rate = base.multiply(nz(rule.getMultiplier()));
                amount = rate;
            } else {
                rate = switch (rule.getBasis()) {
                    case "HOURLY_RATE" -> hourlyRate.multiply(nz(rule.getMultiplier()));
                    case "OVERTIME_RATE" -> overtimeRate.multiply(nz(rule.getMultiplier()));
                    case "FLAT_PER_UNIT" -> nz(rule.getFlatAmount()).multiply(nz(rule.getMultiplier()));
                    default -> throw new IllegalStateException(
                            "Unknown pay basis '" + rule.getBasis() + "' on " + rule.getCategoryCode());
                };
                amount = quantity.multiply(rate);
            }

            earnings.add(new Line(rule.getCategoryCode(), rule.getNote(), quantity, rate, amount));
            gross = gross.add(amount);
            exempt = exempt.add(quantity.multiply(nz(rule.getExemptPerUnit())));

            if ("ONSHORE_HOURS".equals(rule.getCategoryCode())) {
                onshoreAmount = onshoreAmount.add(amount);
            }
        }

        // Any quantity the employee recorded that nothing prices is a hole in the
        // rule catalog, not a zero — say so rather than quietly pay nothing.
        for (String code : qty.keySet()) {
            if (nz(qty.get(code)).signum() == 0) continue;
            boolean priced = rules.stream().anyMatch(r -> r.getCategoryCode().equals(code));
            boolean manual = code.equals("EXCESS_HOURS")
                    || code.equals("VACATION_HOURS") || code.equals("SICK_LEAVE_HOURS");
            if (!priced && !manual) {
                warnings.add("No pay rule for recorded category " + code
                        + " — those quantities are not being paid.");
            }
        }

        // Excess / MEWA — configured per employee, never inferred (PRD §3.1).
        BigDecimal excess = BigDecimal.ZERO;
        if (in.excessRule() != null && in.excessRule().coversPeriodStart(in.periodStart())) {
            EmployeeExcessRule r = in.excessRule();
            excess = switch (r.getMethod()) {
                case "PERCENT_OF_ONSHORE" -> onshoreAmount.multiply(nz(r.getPercentage()));
                case "UNITS_AT_RATE" -> nz(r.getUnits()).multiply(hourlyRate)
                        .multiply(nz(r.getMultiplier()));
                default -> throw new IllegalStateException(
                        "Unknown excess method '" + r.getMethod() + "'");
            };
            earnings.add(new Line("EXCESS_HOURS", "Excess / MEWA", null, null, excess));
            gross = gross.add(excess);
        } else if (nz(qty.get("EXCESS_HOURS")).signum() > 0) {
            warnings.add("Excess hours were recorded but this employee has no excess rule "
                    + "configured, so no excess amount is paid. Configure the rule before "
                    + "running payroll.");
        }

        // Manual workbook columns.
        BigDecimal extra = nz(in.extraAmount());
        BigDecimal vacation = nz(in.vacationAmount());
        BigDecimal sick = nz(in.sickLeaveAmount());
        if (extra.signum() != 0) earnings.add(new Line("EXTRA", "Extra amount", null, null, extra));
        if (vacation.signum() != 0) earnings.add(new Line("VACATION", "Vacation AZN", null, null, vacation));
        if (sick.signum() != 0) earnings.add(new Line("SICK", "Sick leave AZN", null, null, sick));
        gross = gross.add(extra).add(vacation).add(sick);

        // ---- statutory (workbook AK–AN) ------------------------------------
        BigDecimal lifeIns = nz(in.lifeInsurance());
        StatutoryDeductionCalculator.Result stat = statutory.calculate(gross, exempt, sick, lifeIns);
        BigDecimal incomeTax = stat.incomeTax();
        BigDecimal spf = stat.spf();
        BigDecimal unemployment = stat.unemploymentFund();
        BigDecimal insurance = stat.compulsoryInsurance();

        BigDecimal azercell = nz(in.azercell());
        BigDecimal advance = nz(in.advance());
        BigDecimal totalDeductions = stat.total().add(lifeIns).add(azercell).add(advance);
        BigDecimal net = gross.subtract(totalDeductions);

        return new Result(
                money(hourlyRate), money(overtimeRate),
                earnings.stream().map(TimesheetPayCalculator::roundLine).toList(),
                money(gross), money(exempt),
                money(incomeTax), money(spf), money(unemployment), money(insurance),
                money(lifeIns), money(azercell), money(advance),
                money(totalDeductions), money(net),
                warnings);
    }

    private static Line roundLine(Line l) {
        return new Line(l.categoryCode(), l.label(), l.quantity(),
                l.rate() == null ? null : money(l.rate()), money(l.amount()));
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
