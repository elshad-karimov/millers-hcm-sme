package az.millers.hcm.budgeting.api.dto;

import java.math.BigDecimal;

/**
 * HCM_20 M426 — Monthly payroll cost forecast projection.
 */
public record MonthlyForecast(
        String month,                  // YYYY-MM
        BigDecimal forecastCost,       // cumulative monthly cost
        int cumulativeHires,           // net hires to date
        BigDecimal cumulativeExits,    // net exits to date
        BigDecimal monthlyGrowth       // growth adjustment this month
) {}
