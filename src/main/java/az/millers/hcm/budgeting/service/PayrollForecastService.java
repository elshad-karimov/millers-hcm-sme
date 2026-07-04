package az.millers.hcm.budgeting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.budgeting.api.dto.MonthlyForecast;

/**
 * HCM_20 M426 — Payroll cost forecast (PRD §20.4).
 * Current monthly cost (active employees' salary) + planned hires from staffing.hiring_plan_line
 * − expected exits from staffing.attrition_forecast; apply annual growth %.
 */
@Service
public class PayrollForecastService {

    private static final String TENANT = "default";
    private static final BigDecimal DEFAULT_GROWTH_PCT = new BigDecimal("5.0");

    private final NamedParameterJdbcTemplate jdbc;

    public PayrollForecastService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Project monthly payroll cost forward for the given number of months.
     * @param months      number of months to project
     * @param growthPct   annual salary increase percentage (default 5%)
     * @return list of monthly forecasts
     */
    @Transactional(readOnly = true)
    public List<MonthlyForecast> projectMonthlyCost(int months, BigDecimal growthPct) {
        if (months < 1 || months > 120) {
            throw new IllegalArgumentException("months must be between 1 and 120");
        }
        BigDecimal annualGrowth = growthPct != null ? growthPct : DEFAULT_GROWTH_PCT;

        // 1. Current monthly cost = SUM of active employees' monthly_base_salary
        BigDecimal currentMonthlyCost = jdbc.queryForObject(
                "SELECT COALESCE(SUM(ec.monthly_base_salary), 0) " +
                "FROM payroll.employee_compensation ec " +
                "JOIN core_hr.employee e ON e.id = ec.employee_id " +
                "WHERE e.tenant_id = :tenant " +
                "  AND e.employment_status = 'ACTIVE' " +
                "  AND ec.effective_from <= :today " +
                "  AND (ec.effective_to IS NULL OR ec.effective_to >= :today)",
                Map.of("tenant", TENANT, "today", LocalDate.now()),
                BigDecimal.class
        );

        if (currentMonthlyCost == null) currentMonthlyCost = BigDecimal.ZERO;

        // 2. Average salary (for planned hires + exits)
        BigDecimal avgSalary = jdbc.queryForObject(
                "SELECT COALESCE(AVG(ec.monthly_base_salary), 0) " +
                "FROM payroll.employee_compensation ec " +
                "JOIN core_hr.employee e ON e.id = ec.employee_id " +
                "WHERE e.tenant_id = :tenant " +
                "  AND e.employment_status = 'ACTIVE' " +
                "  AND ec.effective_from <= :today " +
                "  AND (ec.effective_to IS NULL OR ec.effective_to >= :today)",
                Map.of("tenant", TENANT, "today", LocalDate.now()),
                BigDecimal.class
        );
        if (avgSalary == null || avgSalary.compareTo(BigDecimal.ZERO) == 0) {
            avgSalary = new BigDecimal("5000"); // fallback
        }

        // 3. Planned hires per month (from staffing.hiring_plan_line)
        List<Map<String, Object>> hires = jdbc.queryForList(
                "SELECT DATE_TRUNC('month', target_start_date)::date AS month, SUM(headcount) AS hc " +
                "FROM staffing.hiring_plan_line " +
                "WHERE tenant_id = :tenant " +
                "  AND recruitment_status != 'CANCELLED' " +
                "  AND target_start_date >= :today " +
                "GROUP BY DATE_TRUNC('month', target_start_date)",
                Map.of("tenant", TENANT, "today", LocalDate.now())
        );
        Map<YearMonth, Integer> hiresPerMonth = hires.stream()
                .collect(Collectors.toMap(
                        m -> {
                            LocalDate ld = ((java.sql.Date) m.get("month")).toLocalDate();
                            return YearMonth.from(ld);
                        },
                        m -> ((Number) m.get("hc")).intValue()
                ));

        // 4. Expected exits per month (from staffing.attrition_forecast)
        List<Map<String, Object>> exits = jdbc.queryForList(
                "SELECT DATE_TRUNC('month', forecast_date)::date AS month, SUM(expected_exits) AS exits " +
                "FROM staffing.attrition_forecast " +
                "WHERE tenant_id = :tenant " +
                "  AND forecast_date >= :today " +
                "GROUP BY DATE_TRUNC('month', forecast_date)",
                Map.of("tenant", TENANT, "today", LocalDate.now())
        );
        Map<YearMonth, BigDecimal> exitsPerMonth = exits.stream()
                .collect(Collectors.toMap(
                        m -> {
                            LocalDate ld = ((java.sql.Date) m.get("month")).toLocalDate();
                            return YearMonth.from(ld);
                        },
                        m -> new BigDecimal(m.get("exits").toString())
                ));

        // 5. Build monthly forecasts
        List<MonthlyForecast> forecasts = new ArrayList<>();
        YearMonth current = YearMonth.now();
        BigDecimal cumulativeCost = currentMonthlyCost;
        int cumulativeHires = 0;
        BigDecimal cumulativeExits = BigDecimal.ZERO;

        for (int i = 0; i < months; i++) {
            YearMonth month = current.plusMonths(i);

            // Apply annual growth compounded monthly (simplified: 1/12 of annual % each month)
            BigDecimal monthlyGrowthFactor = annualGrowth.divide(new BigDecimal("1200"), 6, RoundingMode.HALF_UP);
            BigDecimal growthAdjustment = cumulativeCost.multiply(monthlyGrowthFactor);

            // Planned hires
            int monthHires = hiresPerMonth.getOrDefault(month, 0);
            BigDecimal hireCost = avgSalary.multiply(new BigDecimal(monthHires));

            // Expected exits
            BigDecimal monthExits = exitsPerMonth.getOrDefault(month, BigDecimal.ZERO);
            BigDecimal exitCost = avgSalary.multiply(monthExits);

            // Net change
            BigDecimal netChange = growthAdjustment.add(hireCost).subtract(exitCost);
            cumulativeCost = cumulativeCost.add(netChange);

            cumulativeHires += monthHires;
            cumulativeExits = cumulativeExits.add(monthExits);

            forecasts.add(new MonthlyForecast(
                    month.toString(),
                    cumulativeCost.setScale(2, RoundingMode.HALF_UP),
                    cumulativeHires,
                    cumulativeExits.setScale(2, RoundingMode.HALF_UP),
                    growthAdjustment.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return forecasts;
    }
}
