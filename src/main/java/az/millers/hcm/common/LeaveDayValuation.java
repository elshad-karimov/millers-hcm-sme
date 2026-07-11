package az.millers.hcm.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of truth for valuing one day of leave from a monthly salary.
 *
 * <p>The formula {@code monthlyBaseSalary / workingDaysPerMonth} (scale-4,
 * HALF_UP) was previously inlined in four places — leave liability, encashment,
 * unpaid-leave deduction, and final-settlement — each with its own divisor
 * constant, and they had drifted: the three leave services used 22 while
 * termination used 21.67, so the same unused day paid different money depending
 * on the path. Standardised on <b>22</b> working days/month (signed-off decision);
 * all four now call this helper.
 */
public final class LeaveDayValuation {

    private LeaveDayValuation() {}

    /** Default working days per month used to value one day of leave. */
    public static final int DEFAULT_WORKING_DAYS_PER_MONTH = 22;

    /**
     * Daily rate = monthlyBaseSalary / workingDaysPerMonth (scale 4, HALF_UP).
     * Returns ZERO for a non-positive salary. A non-positive
     * {@code workingDaysPerMonth} falls back to {@link #DEFAULT_WORKING_DAYS_PER_MONTH}.
     */
    public static BigDecimal dailyRate(BigDecimal monthlyBaseSalary, int workingDaysPerMonth) {
        if (monthlyBaseSalary == null || monthlyBaseSalary.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int wdpm = workingDaysPerMonth > 0 ? workingDaysPerMonth : DEFAULT_WORKING_DAYS_PER_MONTH;
        return monthlyBaseSalary.divide(BigDecimal.valueOf(wdpm), 4, RoundingMode.HALF_UP);
    }

    /** Daily rate at the default working days/month. */
    public static BigDecimal dailyRate(BigDecimal monthlyBaseSalary) {
        return dailyRate(monthlyBaseSalary, DEFAULT_WORKING_DAYS_PER_MONTH);
    }

    /** Value of {@code days} days = dailyRate × days (scale 2, HALF_UP). */
    public static BigDecimal valueOfDays(BigDecimal monthlyBaseSalary, int workingDaysPerMonth, BigDecimal days) {
        return dailyRate(monthlyBaseSalary, workingDaysPerMonth)
                .multiply(days).setScale(2, RoundingMode.HALF_UP);
    }
}
