package az.millers.hcm.analytics.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.analytics.api.dto.ExecutiveSummary;
import az.millers.hcm.analytics.api.dto.ExecutiveSummary.ComplianceDeadlineItem;
import az.millers.hcm.analytics.api.dto.ExecutiveSummary.HeadcountTrendSummary;
import az.millers.hcm.analytics.api.dto.ExecutiveSummary.PayrollCostTrendSummary;

/**
 * M475 — Executive analytics summary service.
 */
@Service
public class ExecutiveAnalyticsService {

    private static final String TENANT = "default";

    private final NamedParameterJdbcTemplate jdbc;
    private final KpiValueService kpiValueService;

    public ExecutiveAnalyticsService(NamedParameterJdbcTemplate jdbc,
                                    KpiValueService kpiValueService) {
        this.jdbc = jdbc;
        this.kpiValueService = kpiValueService;
    }

    @Transactional(readOnly = true)
    public ExecutiveSummary summary() {
        // Headcount trend
        Long currentHeadcount = (Long) kpiValueService.value("HEADCOUNT_ACTIVE");
        Long previousHeadcount = headcountForMonth(YearMonth.now().minusMonths(1));
        String headcountTrend = currentHeadcount > previousHeadcount ? "up" :
                                currentHeadcount < previousHeadcount ? "down" : "stable";
        HeadcountTrendSummary headcountTrendSummary = new HeadcountTrendSummary(
                currentHeadcount, previousHeadcount, headcountTrend);

        // Turnover 12M
        BigDecimal turnover12m = (BigDecimal) kpiValueService.value("TURNOVER_12M");

        // Payroll cost trend
        BigDecimal currentPayrollCost = (BigDecimal) kpiValueService.value("PAYROLL_COST_MONTHLY");
        BigDecimal previousPayrollCost = payrollCostForMonth(YearMonth.now().minusMonths(1));
        String payrollTrend = currentPayrollCost.compareTo(previousPayrollCost) > 0 ? "up" :
                              currentPayrollCost.compareTo(previousPayrollCost) < 0 ? "down" : "stable";
        PayrollCostTrendSummary payrollCostTrendSummary = new PayrollCostTrendSummary(
                currentPayrollCost, previousPayrollCost, payrollTrend);

        // eNPS
        Integer enps = (Integer) kpiValueService.value("ENPS");

        // Upcoming compliance deadlines (next 30 days)
        List<ComplianceDeadlineItem> upcomingDeadlines = jdbc.query(
                "SELECT title, next_due, 'upcoming' AS status " +
                "FROM compliance.compliance_deadline " +
                "WHERE tenant_id = :tenant AND next_due BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days' " +
                "ORDER BY next_due LIMIT 5",
                new MapSqlParameterSource("tenant", TENANT),
                (rs, i) -> new ComplianceDeadlineItem(
                        rs.getString("title"),
                        rs.getObject("next_due", LocalDate.class).format(DateTimeFormatter.ISO_DATE),
                        rs.getString("status")));

        // Attrition high risk count (from M476 if exists, else 0)
        Long attritionHighRiskCount = 0L;
        try {
            attritionHighRiskCount = jdbc.queryForObject(
                    "SELECT count(*) FROM analytics.attrition_risk " +
                    "WHERE tenant_id = :tenant AND score >= 70",
                    new MapSqlParameterSource("tenant", TENANT),
                    Long.class);
        } catch (Exception e) {
            // Table doesn't exist yet (M476 not run)
        }

        return new ExecutiveSummary(
                headcountTrendSummary,
                turnover12m,
                payrollCostTrendSummary,
                enps,
                upcomingDeadlines,
                attritionHighRiskCount != null ? attritionHighRiskCount : 0L);
    }

    private Long headcountForMonth(YearMonth month) {
        LocalDate monthEnd = month.atEndOfMonth();
        return jdbc.queryForObject(
                "SELECT count(*) FROM core_hr.employee e " +
                "WHERE e.tenant_id = :tenant AND e.hire_date <= :end " +
                "AND NOT EXISTS (" +
                "   SELECT 1 FROM lifecycle.termination_request t " +
                "   WHERE t.employee_id = e.id AND t.status = 'PROCESSED' AND t.effective_date <= :end" +
                ")",
                new MapSqlParameterSource()
                        .addValue("tenant", TENANT)
                        .addValue("end", monthEnd),
                Long.class);
    }

    private BigDecimal payrollCostForMonth(YearMonth month) {
        BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(net_pay), 0) FROM payroll.payroll_result " +
                "WHERE tenant_id = :tenant AND payroll_year = :year AND payroll_month = :month",
                new MapSqlParameterSource()
                        .addValue("tenant", TENANT)
                        .addValue("year", month.getYear())
                        .addValue("month", month.getMonthValue()),
                BigDecimal.class);
        return total != null ? total : BigDecimal.ZERO;
    }
}
