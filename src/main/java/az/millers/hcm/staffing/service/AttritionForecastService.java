package az.millers.hcm.staffing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.staffing.domain.AttritionForecast;
import az.millers.hcm.staffing.repo.AttritionForecastRepository;

/**
 * M423: Attrition forecast — historical, contract expiry, retirement.
 */
@Service
public class AttritionForecastService {

    private static final String TENANT_ID = "default";

    private final AttritionForecastRepository forecasts;
    private final NamedParameterJdbcTemplate jdbc;

    public AttritionForecastService(AttritionForecastRepository forecasts,
                                     NamedParameterJdbcTemplate jdbc) {
        this.forecasts = forecasts;
        this.jdbc = jdbc;
    }

    @Transactional
    public List<AttritionForecast> forecast(UUID planId, LocalDate horizonDate) {
        // Delete existing forecasts for plan (regenerate)
        if (planId != null) {
            forecasts.deleteByPlanId(planId);
        }

        LocalDate today = LocalDate.now();
        LocalDate trailing12Start = today.minusMonths(12);

        List<AttritionForecast> results = new ArrayList<>();

        // 1. Historical turnover per org-unit (trailing 12 months)
        String historicalSql = """
            SELECT
                e.department_id AS org_unit_id,
                COUNT(*) AS termination_count
            FROM lifecycle.termination t
            JOIN core_hr.employee e ON e.id = t.employee_id
            WHERE t.effective_date >= :start
              AND t.effective_date < :end
              AND e.tenant_id = :tenantId
              AND e.department_id IS NOT NULL
            GROUP BY e.department_id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", trailing12Start)
                .addValue("end", today)
                .addValue("tenantId", TENANT_ID);

        List<Map<String, Object>> historical = jdbc.queryForList(historicalSql, params);
        for (Map<String, Object> row : historical) {
            UUID orgUnitId = (UUID) row.get("org_unit_id");
            int count = ((Number) row.get("termination_count")).intValue();

            AttritionForecast f = new AttritionForecast();
            f.setTenantId(TENANT_ID);
            f.setPlanId(planId);
            f.setOrgUnitId(orgUnitId);
            f.setForecastDate(horizonDate != null ? horizonDate : today.plusMonths(12));
            f.setExpectedExits(new BigDecimal(count).setScale(2, RoundingMode.HALF_UP));
            f.setBasis("HISTORICAL");
            f.setDetail("Trailing 12-month terminations annualized");
            results.add(f);
        }

        // 2. Contract expiry (fixed-term contracts expiring within horizon)
        if (horizonDate != null) {
            String expirySQL = """
                SELECT
                    e.department_id AS org_unit_id,
                    COUNT(DISTINCT e.id) AS expiring_count
                FROM lifecycle.employment_contract ec
                JOIN core_hr.employee e ON e.id = ec.employee_id
                WHERE ec.end_date IS NOT NULL
                  AND ec.end_date BETWEEN :today AND :horizon
                  AND e.tenant_id = :tenantId
                  AND e.department_id IS NOT NULL
                GROUP BY e.department_id
                """;

            MapSqlParameterSource expiryParams = new MapSqlParameterSource()
                    .addValue("today", today)
                    .addValue("horizon", horizonDate)
                    .addValue("tenantId", TENANT_ID);

            List<Map<String, Object>> expiring = jdbc.queryForList(expirySQL, expiryParams);
            for (Map<String, Object> row : expiring) {
                UUID orgUnitId = (UUID) row.get("org_unit_id");
                int count = ((Number) row.get("expiring_count")).intValue();

                AttritionForecast f = new AttritionForecast();
                f.setTenantId(TENANT_ID);
                f.setPlanId(planId);
                f.setOrgUnitId(orgUnitId);
                f.setForecastDate(horizonDate);
                f.setExpectedExits(new BigDecimal(count).setScale(2, RoundingMode.HALF_UP));
                f.setBasis("CONTRACT_EXPIRY");
                f.setDetail("Fixed-term contracts expiring before " + horizonDate);
                results.add(f);
            }

            // 3. Retirement (age >= 63 within horizon)
            // Note: birth_date is encrypted in Employee, so using JDBC projection here is tricky
            // For now, skip retirement forecast (deferred seam)
        }

        return forecasts.saveAll(results);
    }

    @Transactional(readOnly = true)
    public List<AttritionForecast> getByPlan(UUID planId) {
        return forecasts.findByTenantIdAndPlanIdOrderByOrgUnitIdAsc(TENANT_ID, planId);
    }
}
