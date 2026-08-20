package az.millers.hcm.analytics.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.analytics.api.dto.ExecutiveSummary;
import az.millers.hcm.analytics.api.dto.ExecutiveSummary.ComplianceDeadlineItem;
import az.millers.hcm.analytics.api.dto.ExecutiveSummary.HeadcountTrendSummary;
import az.millers.hcm.analytics.api.dto.ExecutiveSummary.PayrollCostTrendSummary;
import az.millers.hcm.compliance.service.ComplianceDeadlineService;

/**
 * M475 — Executive analytics summary service.
 */
@Service
public class ExecutiveAnalyticsService {


    private final NamedParameterJdbcTemplate jdbc;
    private final KpiValueService kpiValueService;
    private final ComplianceDeadlineService complianceDeadlines;

    public ExecutiveAnalyticsService(NamedParameterJdbcTemplate jdbc,
                                    KpiValueService kpiValueService,
                                    ComplianceDeadlineService complianceDeadlines) {
        this.jdbc = jdbc;
        this.kpiValueService = kpiValueService;
        this.complianceDeadlines = complianceDeadlines;
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

        // Upcoming compliance deadlines (next 30 days).
        //
        // This used to SELECT a next_due column. There is no such column:
        // compliance_deadline stores a recurrence RULE — frequency, due_day,
        // month — and the next occurrence is derived from it. The date the
        // query wanted has to be computed, so it asks the service that already
        // computes it rather than growing a second, divergent notion of when a
        // quarterly deadline next falls due.
        List<ComplianceDeadlineItem> upcomingDeadlines = complianceDeadlines
                .getUpcoming(30).stream()
                .sorted(Comparator.comparing(ComplianceDeadlineService.UpcomingDeadline::nextDue))
                .limit(5)
                .map(d -> new ComplianceDeadlineItem(
                        d.title(),
                        d.nextDue().format(DateTimeFormatter.ISO_DATE),
                        "upcoming"))
                .toList();

        // Attrition high risk count (from M476 if exists, else 0)
        Long attritionHighRiskCount = 0L;
        try {
            attritionHighRiskCount = jdbc.queryForObject(
                    "SELECT count(*) FROM analytics.attrition_risk " +
                    "WHERE tenant_id = :tenant AND score >= 70",
                    new MapSqlParameterSource("tenant", TenantContext.current()),
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
                        .addValue("tenant", TenantContext.current())
                        .addValue("end", monthEnd),
                Long.class);
    }

    /**
     * Net payroll cost for one month.
     *
     * <p>Named three columns that payroll.payroll_result does not have —
     * net_pay, payroll_year and payroll_month — so it threw
     * BadSqlGrammarException on every call and 500'd the whole executive
     * summary. The amount is net_amount, and the period lives on the parent
     * payroll_run as period_year/period_month, which is why this now joins.
     * Mirrors the working query in
     * {@link KpiValueService#payrollCostMonthly()}.
     */
    private BigDecimal payrollCostForMonth(YearMonth month) {
        BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(pr.net_amount), 0) FROM payroll.payroll_result pr " +
                "WHERE EXISTS (" +
                "  SELECT 1 FROM payroll.payroll_run run " +
                "  WHERE run.id = pr.run_id " +
                "    AND run.tenant_id = :tenant " +
                "    AND run.period_year = :year " +
                "    AND run.period_month = :month" +
                ")",
                new MapSqlParameterSource()
                        .addValue("tenant", TenantContext.current())
                        .addValue("year", month.getYear())
                        .addValue("month", month.getMonthValue()),
                BigDecimal.class);
        return total != null ? total : BigDecimal.ZERO;
    }
}
