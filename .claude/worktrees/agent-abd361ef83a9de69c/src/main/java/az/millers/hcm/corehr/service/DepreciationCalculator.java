package az.millers.hcm.corehr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import az.millers.hcm.corehr.domain.DepreciationMethod;

/**
 * M128 — pure-static depreciation math for {@code core_hr.employee_asset}.
 *
 * <p>Two algorithms in Phase 1:
 * <ul>
 *   <li>{@link DepreciationMethod#STRAIGHT_LINE} — flat monthly hit
 *       {@code (cost − salvage) / lifeMonths}, book value floors at
 *       salvage in the last month.</li>
 *   <li>{@link DepreciationMethod#DECLINING_BALANCE} — annual percentage
 *       applied to the current book value, distributed across 12 months
 *       as {@code rate/12 × bookValue} per month, floored at salvage.
 *       The annual rate defaults to {@code 2 / lifeYears} (the "double
 *       declining" convention) when no explicit rate is configured.</li>
 * </ul>
 *
 * <p>Mockito-free so the rounding + floor rules get pinned by plain
 * JUnit (Java 25 class-file v69 isn't supported by Byte Buddy).
 *
 * <p>All monetary values use {@code BigDecimal} at scale 2 with
 * {@link RoundingMode#HALF_UP} — the accountant's standard.
 */
public final class DepreciationCalculator {

    /** Currency rounding scale — always 2 dp, half-up. */
    public static final int SCALE = 2;
    public static final RoundingMode ROUND = RoundingMode.HALF_UP;

    /** One row in the schedule. */
    public record Period(
            /** 1-based month index (month 1 = first month after purchase). */
            int period,
            /** Period-start date (purchaseDate + (period-1) months). */
            LocalDate periodStart,
            BigDecimal openingValue,
            BigDecimal depreciation,
            BigDecimal closingValue) {}

    private DepreciationCalculator() {}

    /**
     * Compute the full month-by-month schedule. Returns an empty list
     * when the asset is configured as NONE, has no cost, no purchase
     * date, or a non-positive life — the caller renders "no schedule"
     * rather than blowing up.
     */
    public static List<Period> schedule(BigDecimal purchaseCost,
                                         LocalDate purchaseDate,
                                         Integer usefulLifeMonths,
                                         BigDecimal salvageValue,
                                         DepreciationMethod method,
                                         BigDecimal decliningRatePercent) {
        if (method == null || method == DepreciationMethod.NONE) return List.of();
        if (purchaseCost == null || purchaseCost.signum() <= 0) return List.of();
        if (purchaseDate == null) return List.of();
        if (usefulLifeMonths == null || usefulLifeMonths <= 0) return List.of();

        BigDecimal cost = purchaseCost.setScale(SCALE, ROUND);
        BigDecimal salvage = salvageValue == null
                ? BigDecimal.ZERO.setScale(SCALE)
                : salvageValue.setScale(SCALE, ROUND);
        if (salvage.compareTo(cost) > 0) salvage = cost; // sanity

        return switch (method) {
            case STRAIGHT_LINE -> straightLine(cost, purchaseDate, usefulLifeMonths, salvage);
            case DECLINING_BALANCE ->
                    decliningBalance(cost, purchaseDate, usefulLifeMonths, salvage,
                            decliningRatePercent);
            case NONE -> List.of();
        };
    }

    private static List<Period> straightLine(BigDecimal cost, LocalDate start,
                                              int life, BigDecimal salvage) {
        BigDecimal depreciable = cost.subtract(salvage);
        BigDecimal monthly = depreciable.divide(
                BigDecimal.valueOf(life), SCALE, ROUND);
        List<Period> out = new ArrayList<>(life);
        BigDecimal book = cost;
        for (int i = 1; i <= life; i++) {
            BigDecimal opening = book;
            BigDecimal periodAmount = monthly;
            // Last period: snap down to salvage to absorb rounding crumbs.
            if (i == life) {
                periodAmount = opening.subtract(salvage).setScale(SCALE, ROUND);
                if (periodAmount.signum() < 0) periodAmount = BigDecimal.ZERO.setScale(SCALE);
            } else if (opening.subtract(periodAmount).compareTo(salvage) < 0) {
                // Mid-schedule floor protection.
                periodAmount = opening.subtract(salvage).setScale(SCALE, ROUND);
                if (periodAmount.signum() < 0) periodAmount = BigDecimal.ZERO.setScale(SCALE);
            }
            BigDecimal closing = opening.subtract(periodAmount).setScale(SCALE, ROUND);
            out.add(new Period(i, start.plusMonths(i - 1), opening, periodAmount, closing));
            book = closing;
        }
        return out;
    }

    private static List<Period> decliningBalance(BigDecimal cost, LocalDate start,
                                                  int life, BigDecimal salvage,
                                                  BigDecimal decliningRatePercent) {
        // Annual rate as a decimal. Default = 2 / lifeYears ("double declining").
        BigDecimal lifeYears = BigDecimal.valueOf(life)
                .divide(BigDecimal.valueOf(12), 6, ROUND);
        BigDecimal annualRate;
        if (decliningRatePercent != null && decliningRatePercent.signum() > 0) {
            annualRate = decliningRatePercent.divide(BigDecimal.valueOf(100), 6, ROUND);
        } else {
            annualRate = BigDecimal.valueOf(2).divide(lifeYears, 6, ROUND);
        }
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 6, ROUND);

        List<Period> out = new ArrayList<>(life);
        BigDecimal book = cost;
        for (int i = 1; i <= life; i++) {
            BigDecimal opening = book;
            BigDecimal periodAmount = opening.multiply(monthlyRate)
                    .setScale(SCALE, ROUND);
            // Floor at salvage. Last month snaps to salvage exactly so
            // rounding crumbs don't accumulate forever.
            if (i == life) {
                periodAmount = opening.subtract(salvage).setScale(SCALE, ROUND);
                if (periodAmount.signum() < 0) periodAmount = BigDecimal.ZERO.setScale(SCALE);
            } else if (opening.subtract(periodAmount).compareTo(salvage) < 0) {
                periodAmount = opening.subtract(salvage).setScale(SCALE, ROUND);
                if (periodAmount.signum() < 0) periodAmount = BigDecimal.ZERO.setScale(SCALE);
            }
            BigDecimal closing = opening.subtract(periodAmount).setScale(SCALE, ROUND);
            out.add(new Period(i, start.plusMonths(i - 1), opening, periodAmount, closing));
            book = closing;
        }
        return out;
    }

    /**
     * Book value as of {@code asOf}, derived from the schedule. Returns
     * the purchase cost if {@code asOf} is on or before the purchase
     * date, and the salvage value if it's at or past the end-of-life.
     * Null when the asset doesn't depreciate.
     */
    public static BigDecimal bookValueOn(List<Period> schedule,
                                          LocalDate purchaseDate,
                                          BigDecimal purchaseCost,
                                          BigDecimal salvageValue,
                                          LocalDate asOf) {
        if (schedule == null || schedule.isEmpty() || asOf == null) return null;
        if (purchaseCost == null) return null;
        BigDecimal cost = purchaseCost.setScale(SCALE, ROUND);
        if (purchaseDate != null && !asOf.isAfter(purchaseDate)) return cost;
        BigDecimal salvage = salvageValue == null
                ? BigDecimal.ZERO.setScale(SCALE)
                : salvageValue.setScale(SCALE, ROUND);
        // Walk the schedule and find the latest period whose periodStart ≤ asOf.
        BigDecimal book = cost;
        for (Period p : schedule) {
            if (p.periodStart().isAfter(asOf)) break;
            book = p.closingValue();
        }
        // Floor at salvage in case the asset is past end-of-life.
        return book.compareTo(salvage) < 0 ? salvage : book;
    }
}
