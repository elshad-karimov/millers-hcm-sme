package az.millers.hcm.payroll.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import az.millers.hcm.payroll.timepay.StatutoryDeductionCalculator;
import az.millers.hcm.payroll.timepay.TimePayRule;

/**
 * Prices one approved month according to the employee's calculation profile.
 *
 * <p>Transcribed from the July 2026 material: the WhatsApp discussion with Emil
 * and the three company spreadsheets. Every multiplier is configuration read
 * from {@link CalculationProfile} or {@link TimePayRule} — none is written into
 * this class. Pure arithmetic: no repository, no clock, no I/O, so it can be
 * pinned against the company's own figures in a unit test.
 *
 * <h2>Why a profile at all</h2>
 * There is no universal salary formula. The offshore portion alone is priced
 * four different ways depending on the contract, and the difference is not
 * cosmetic — a rotation employee with one offshore hour is paid
 * {@code base × 1.75} in full, while an onshore-contract employee with one
 * offshore hour is paid {@code 1 × rate × 1.75}.
 *
 * <h2>Where it refuses</h2>
 * Three inputs are unresolved in the source material and the engine will not
 * invent them. Each produces a blocker and a zero rather than a plausible
 * number: the rotation excess multiplier (Q2), the monthly excess night term
 * (Q1), and an employee with no profile at all. A payroll error is silent, so
 * the failure has to be loud.
 */
@Component
public class ProfilePayCalculator {

    private static final int RATE_SCALE = 10;
    private static final BigDecimal OVERTIME_FACTOR = BigDecimal.valueOf(2);

    // Categories the profile prices itself. They are held out of the generic
    // catalog loop so offshore is never paid twice by two different routes.
    private static final String OFFSHORE_HOURS = "OFFSHORE_HOURS";
    private static final String OFFSHORE_NIGHT_HOURS = "OFFSHORE_NIGHT_HOURS";
    private static final String QUAYSIDE_HOURS = "QUAYSIDE_HOURS";
    private static final String QUAYSIDE_NIGHT_HOURS = "QUAYSIDE_NIGHT_HOURS";
    private static final String ONSHORE_HOURS = "ONSHORE_HOURS";
    private static final String SICK_LEAVE_HOURS = "SICK_LEAVE_HOURS";
    private static final String EXCESS_HOURS = "EXCESS_HOURS";
    private static final String VACATION_HOURS = "VACATION_HOURS";

    /** Earning codes, so a payslip line is readable without the category code. */
    public static final String EARN_ONSHORE_REGULAR = "ONSHORE_REGULAR";
    public static final String EARN_OFFSHORE_75 = "OFFSHORE_75";
    public static final String EARN_OFFSHORE_ROTA_SALARY = "OFFSHORE_ROTA_SALARY";
    public static final String EARN_OFFSHORE_DERIVED = "OFFSHORE_DERIVED";
    public static final String EARN_QUAYSIDE = "QUAYSIDE";
    public static final String EARN_EXCESS = "EXCESS";
    public static final String EARN_MEWA = "MEWA";

    private final StatutoryDeductionCalculator statutory = new StatutoryDeductionCalculator();

    public record Input(
            CalculationProfile profile,
            BigDecimal baseSalary,
            BigDecimal normHours,
            LocalDate periodStart,
            /** category code -> approved, locked month quantity */
            Map<String, BigDecimal> quantities,
            /** the catalog, with this employee's dated overrides already merged */
            List<TimePayRule> rules,
            /** null when the employee has no MEWA entitlement */
            BigDecimal mewaRate,
            /**
             * Hours released by a balancing-period settlement, supplied only in
             * a settlement month. Null on every other month — a rotation
             * employee's excess is not a monthly event.
             */
            BigDecimal settlementExcessHours,
            BigDecimal extraAmount,
            BigDecimal vacationAmount,
            BigDecimal sickLeaveAmount,
            BigDecimal lifeInsurance,
            BigDecimal azercell,
            BigDecimal advance) {
    }

    public record Line(String earningCode, String label, BigDecimal quantity,
                       BigDecimal rate, BigDecimal amount) {
    }

    public record Result(
            String profileCode,
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
            /** Things the caller should know but which did not stop the sum. */
            List<String> warnings,
            /** Unresolved configuration. A result with blockers is not payable. */
            List<String> blockers) {

        public boolean isPayable() {
            return blockers.isEmpty();
        }
    }

    public Result calculate(Input in) {
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        CalculationProfile profile = in.profile();
        if (profile == null) {
            throw new IllegalArgumentException(
                    "No calculation profile for this employee and period. Pay cannot be "
                            + "derived without one — the same hours are worth different "
                            + "amounts under different contracts.");
        }
        BigDecimal norm = in.normHours();
        if (norm == null || norm.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Norm working hours must be configured for the period before pay can be "
                            + "calculated — it is the divisor behind every rate.");
        }

        BigDecimal base = nz(in.baseSalary());
        BigDecimal hourlyRate = base.divide(norm, RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal overtimeRate = hourlyRate.multiply(OVERTIME_FACTOR);

        Map<String, BigDecimal> qty = in.quantities() == null
                ? Map.of() : new LinkedHashMap<>(in.quantities());

        List<Line> earnings = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal exempt = BigDecimal.ZERO;

        // ---- 1. the offshore portion, priced four different ways ------------
        BigDecimal offshoreHours = nz(qty.get(OFFSHORE_HOURS));
        BigDecimal offshoreNight = nz(qty.get(OFFSHORE_NIGHT_HOURS));
        BigDecimal sickHours = nz(qty.get(SICK_LEAVE_HOURS));
        BigDecimal offshoreAmount = BigDecimal.ZERO;

        if (profile.nightTreatmentUnconfirmed()
                && (offshoreNight.signum() > 0 || nz(qty.get(QUAYSIDE_NIGHT_HOURS)).signum() > 0)) {
            warnings.add("Night hours are being treated as a subset of the offshore/quayside "
                    + "hours rather than as extra hours, which is the reading validated against "
                    + "the January 2026 workbook. The July spreadsheets read the other way. "
                    + "Confirm BLOCKERS Q1 before this month is paid.");
        }

        switch (profile.getOffshoreSalaryMode()) {
            case CalculationProfile.OFFSHORE_NONE -> {
                if (offshoreHours.signum() > 0) {
                    blockers.add("Offshore hours were recorded but profile "
                            + profile.getCode() + " has no offshore component, so they are not "
                            + "paid. Either the hours or the profile assignment is wrong.");
                }
            }
            case CalculationProfile.OFFSHORE_HOURLY -> {
                BigDecimal payable = profile.addsNightHoursToBase()
                        ? offshoreHours.add(offshoreNight) : offshoreHours;
                if (payable.signum() > 0) {
                    BigDecimal rate = hourlyRate.multiply(nz(profile.getOffshoreMultiplier()));
                    offshoreAmount = payable.multiply(rate);
                    earnings.add(new Line(EARN_OFFSHORE_75, "Offshore hours", payable, rate,
                            offshoreAmount));
                }
            }
            case CalculationProfile.OFFSHORE_MONTHLY_BASE -> {
                // Rotation: base x 1.75 every qualifying month, regardless of how
                // many offshore days were worked. Any offshore hour qualifies.
                if (offshoreHours.add(offshoreNight).signum() > 0) {
                    offshoreAmount = base.multiply(nz(profile.getOffshoreMultiplier()));
                    earnings.add(new Line(EARN_OFFSHORE_ROTA_SALARY,
                            "Offshore rotation salary", null, null, offshoreAmount));
                } else {
                    warnings.add("No offshore hours this month, so the rotation salary "
                            + "(base x " + profile.getOffshoreMultiplier() + ") is not paid. "
                            + "Whether a full vacation or sick month still qualifies is "
                            + "unresolved — BLOCKERS Q3.");
                }
            }
            case CalculationProfile.OFFSHORE_DERIVED_FROM_NORM -> {
                // The offshore quantity is imputed from the norm, not read from
                // the timesheet: rate x (norm - onshore - sick) x 1.75.
                BigDecimal onshoreHours = nz(qty.get(ONSHORE_HOURS));
                BigDecimal deduct = onshoreHours;
                if (profile.isDerivedOffshoreDeductsSick()) deduct = deduct.add(sickHours);
                BigDecimal derived = norm.subtract(deduct);
                if (derived.signum() < 0) {
                    blockers.add("Onshore plus sick hours (" + deduct + ") exceed the norm ("
                            + norm + "), so the derived offshore quantity is negative. Whether "
                            + "this floors at zero or is an error is unresolved — BLOCKERS Q6.");
                    derived = BigDecimal.ZERO;
                }
                if (offshoreHours.signum() > 0) {
                    warnings.add("This profile derives offshore hours from the norm and ignores "
                            + "the " + offshoreHours + " offshore hours actually recorded. "
                            + "Confirm BLOCKERS Q6.");
                }
                if (derived.signum() > 0) {
                    BigDecimal rate = hourlyRate.multiply(nz(profile.getOffshoreMultiplier()));
                    offshoreAmount = derived.multiply(rate);
                    earnings.add(new Line(EARN_OFFSHORE_DERIVED, "Offshore salary (derived)",
                            derived, rate, offshoreAmount));
                }
            }
            default -> throw new IllegalStateException(
                    "Unknown offshore salary mode '" + profile.getOffshoreSalaryMode()
                            + "' on profile " + profile.getCode());
        }
        gross = gross.add(offshoreAmount);

        // ---- 2. quayside, on the same night-hours question ------------------
        BigDecimal quaysideHours = nz(qty.get(QUAYSIDE_HOURS));
        BigDecimal quaysideNight = nz(qty.get(QUAYSIDE_NIGHT_HOURS));
        BigDecimal quaysidePayable = profile.addsNightHoursToBase()
                ? quaysideHours.add(quaysideNight) : quaysideHours;
        TimePayRule quaysideRule = ruleFor(in.rules(), QUAYSIDE_HOURS);
        if (quaysidePayable.signum() > 0 && quaysideRule != null) {
            BigDecimal rate = hourlyRate.multiply(nz(quaysideRule.getMultiplier()));
            BigDecimal amount = quaysidePayable.multiply(rate);
            earnings.add(new Line(EARN_QUAYSIDE, "Quayside hours", quaysidePayable, rate, amount));
            gross = gross.add(amount);
        }

        // ---- 3. everything the catalog prices per unit ----------------------
        // Night, holiday, quarantine, onshore, onshore overtime and the flat
        // allowances. Offshore and quayside are excluded: the profile owns them.
        BigDecimal onshoreAmount = BigDecimal.ZERO;
        List<TimePayRule> rules = new ArrayList<>(
                in.rules() == null ? List.<TimePayRule>of() : in.rules());
        rules.sort((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()));

        for (TimePayRule rule : rules) {
            String code = rule.getCategoryCode();
            if (!rule.isActive()) continue;
            if (OFFSHORE_HOURS.equals(code) || QUAYSIDE_HOURS.equals(code)) continue;

            BigDecimal quantity = nz(qty.get(code));
            if (quantity.signum() == 0) continue;

            BigDecimal rate = switch (rule.getBasis()) {
                case "HOURLY_RATE" -> hourlyRate.multiply(nz(rule.getMultiplier()));
                case "OVERTIME_RATE" -> overtimeRate.multiply(nz(rule.getMultiplier()));
                case "FLAT_PER_UNIT" -> nz(rule.getFlatAmount()).multiply(nz(rule.getMultiplier()));
                case "MONTHLY_SALARY_MULTIPLE" -> base.multiply(nz(rule.getMultiplier()));
                default -> throw new IllegalStateException(
                        "Unknown pay basis '" + rule.getBasis() + "' on " + code);
            };
            BigDecimal amount = "MONTHLY_SALARY_MULTIPLE".equals(rule.getBasis())
                    ? rate : quantity.multiply(rate);

            earnings.add(new Line(code, rule.getNote(), quantity, rate, amount));
            gross = gross.add(amount);
            exempt = exempt.add(quantity.multiply(nz(rule.getExemptPerUnit())));

            if (ONSHORE_HOURS.equals(code)) onshoreAmount = onshoreAmount.add(amount);
        }

        // A recorded quantity nothing prices is a hole in the catalog, not a zero.
        for (Map.Entry<String, BigDecimal> e : qty.entrySet()) {
            if (nz(e.getValue()).signum() == 0) continue;
            String code = e.getKey();
            if (OFFSHORE_HOURS.equals(code) || QUAYSIDE_HOURS.equals(code)) continue;
            boolean handled = rules.stream().anyMatch(r -> r.getCategoryCode().equals(code))
                    || EXCESS_HOURS.equals(code)
                    || VACATION_HOURS.equals(code)
                    || SICK_LEAVE_HOURS.equals(code);
            if (!handled) {
                warnings.add("No pay rule for recorded category " + code
                        + " — those quantities are not being paid.");
            }
        }

        // ---- 4. excess — monthly, or released by a settlement ---------------
        ExcessOutcome excess = excess(profile, in, qty, hourlyRate, norm, blockers);
        if (excess.amount().signum() != 0) {
            earnings.add(new Line(EARN_EXCESS, excess.label(), excess.hours(),
                    excess.rate(), excess.amount()));
            gross = gross.add(excess.amount());
        }

        // ---- 5. MEWA — per employee, never inferred -------------------------
        if (in.mewaRate() != null && onshoreAmount.signum() != 0) {
            BigDecimal mewa = onshoreAmount.multiply(in.mewaRate());
            earnings.add(new Line(EARN_MEWA, "MEWA", null, in.mewaRate(), mewa));
            gross = gross.add(mewa);
        }

        // ---- 6. amounts that arrive already calculated ----------------------
        BigDecimal extra = nz(in.extraAmount());
        BigDecimal vacation = nz(in.vacationAmount());
        BigDecimal sick = nz(in.sickLeaveAmount());
        if (extra.signum() != 0) earnings.add(new Line("EXTRA", "Extra amount", null, null, extra));
        if (vacation.signum() != 0) {
            earnings.add(new Line("VACATION", "Vacation AZN", null, null, vacation));
        }
        if (sick.signum() != 0) earnings.add(new Line("SICK_PAY", "Sick leave AZN", null, null, sick));
        gross = gross.add(extra).add(vacation).add(sick);

        if (nz(qty.get(VACATION_HOURS)).signum() > 0 && vacation.signum() == 0) {
            warnings.add("Vacation hours are recorded but no vacation amount was supplied. "
                    + "Vacation pay comes from an average-earnings calculation, not from this "
                    + "month's hours, so it cannot be derived here.");
        }

        // ---- 7. deductions ---------------------------------------------------
        BigDecimal lifeIns = nz(in.lifeInsurance());
        StatutoryDeductionCalculator.Result stat = statutory.calculate(gross, exempt, sick, lifeIns);
        BigDecimal azercell = nz(in.azercell());
        BigDecimal advance = nz(in.advance());
        BigDecimal totalDeductions = stat.total().add(lifeIns).add(azercell).add(advance);
        BigDecimal net = gross.subtract(totalDeductions);

        return new Result(
                profile.getCode(), money(hourlyRate), money(overtimeRate),
                earnings.stream().map(ProfilePayCalculator::roundLine).toList(),
                money(gross), money(exempt),
                money(stat.incomeTax()), money(stat.spf()), money(stat.unemploymentFund()),
                money(stat.compulsoryInsurance()),
                money(lifeIns), money(azercell), money(advance),
                money(totalDeductions), money(net),
                warnings, blockers);
    }

    private record ExcessOutcome(BigDecimal hours, BigDecimal rate, BigDecimal amount, String label) {
        static ExcessOutcome none() {
            return new ExcessOutcome(null, null, BigDecimal.ZERO, null);
        }
    }

    private ExcessOutcome excess(CalculationProfile profile, Input in,
                                 Map<String, BigDecimal> qty, BigDecimal hourlyRate,
                                 BigDecimal norm, List<String> blockers) {
        if (profile.settlesExcessMonthly()) {
            // BLOCKERS Q1: whether night hours are extra hours or a subset of
            // the offshore figure decides this sum, and the two readings differ
            // by every night hour worked. Refuse rather than pick one.
            if (profile.nightTreatmentUnconfirmed()
                    && nz(qty.get(OFFSHORE_NIGHT_HOURS)).signum() > 0) {
                blockers.add("Monthly excess cannot be calculated: this month has "
                        + qty.get(OFFSHORE_NIGHT_HOURS) + " offshore night hours and whether "
                        + "they are extra hours or already inside the offshore figure is "
                        + "unresolved (BLOCKERS Q1). The two readings differ by those hours.");
                return ExcessOutcome.none();
            }
            BigDecimal worked = nz(qty.get(OFFSHORE_HOURS)).add(nz(qty.get(ONSHORE_HOURS)));
            if (profile.addsNightHoursToBase()) {
                worked = worked.add(nz(qty.get(OFFSHORE_NIGHT_HOURS)));
            }
            BigDecimal hours = worked.subtract(norm).max(BigDecimal.ZERO);
            if (hours.signum() == 0) return ExcessOutcome.none();

            if (profile.getExcessMultiplier() == null) {
                blockers.add("Profile " + profile.getCode() + " has " + hours
                        + " excess hours but no excess multiplier configured, so they are "
                        + "not paid.");
                return ExcessOutcome.none();
            }
            BigDecimal rate = hourlyRate.multiply(profile.getExcessMultiplier());
            return new ExcessOutcome(hours, rate, hours.multiply(rate), "Excess hours (monthly)");
        }

        if (profile.settlesExcessOverBalancingPeriod()) {
            BigDecimal hours = in.settlementExcessHours();
            if (hours == null || hours.signum() == 0) {
                // Months 1-3 of a balancing period accumulate only.
                return ExcessOutcome.none();
            }
            // BLOCKERS Q2: "2 qat ve 75% elave" is 2 x 1.75 = 3.50 or
            // 2 + 0.75 = 2.75. Around 856 AZN per settlement between them.
            if (profile.getExcessMultiplier() == null) {
                blockers.add("A balancing period settled " + hours + " excess hours, but the "
                        + "rotation excess multiplier is not configured. It is either 3.50 "
                        + "(2 x 1.75) or 2.75 (2 + 0.75) and the source material does not say "
                        + "which — BLOCKERS Q2. Nothing is paid until it is set.");
                return ExcessOutcome.none();
            }
            BigDecimal rate = hourlyRate.multiply(profile.getExcessMultiplier());
            return new ExcessOutcome(hours, rate, hours.multiply(rate),
                    "Excess hours (balancing-period settlement)");
        }

        if (nz(qty.get(EXCESS_HOURS)).signum() > 0) {
            blockers.add("Excess hours were recorded but profile " + profile.getCode()
                    + " does not settle excess, so nothing is paid for them.");
        }
        return ExcessOutcome.none();
    }

    private static TimePayRule ruleFor(List<TimePayRule> rules, String code) {
        if (rules == null) return null;
        return rules.stream()
                .filter(r -> r.isActive() && code.equals(r.getCategoryCode()))
                .findFirst().orElse(null);
    }

    private static Line roundLine(Line l) {
        return new Line(l.earningCode(), l.label(), l.quantity(),
                l.rate() == null ? null : money(l.rate()), money(l.amount()));
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
