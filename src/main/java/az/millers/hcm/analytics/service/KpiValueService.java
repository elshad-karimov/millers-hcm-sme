package az.millers.hcm.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.reporting.service.DashboardService;

/**
 * M474 — KPI value computation service.
 * Maps KPI codes to existing service/JDBC calculations, tenant-filtered.
 */
@Service
public class KpiValueService {

    private static final String TENANT = "default";

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardService dashboardService;

    public KpiValueService(NamedParameterJdbcTemplate jdbc,
                          DashboardService dashboardService) {
        this.jdbc = jdbc;
        this.dashboardService = dashboardService;
    }

    /**
     * Compute value for a single KPI code.
     */
    @Transactional(readOnly = true)
    public Object value(String code) {
        return switch (code) {
            case "HEADCOUNT_ACTIVE" -> headcountActive();
            case "TURNOVER_12M" -> turnover12M();
            case "ABSENCE_RATE" -> absenceRate();
            case "AVG_TENURE_YRS" -> avgTenureYears();
            case "TRAINING_COMPLETION" -> trainingCompletion();
            case "ENPS" -> enps();
            case "OPEN_POSITIONS" -> openPositions();
            case "TIME_TO_HIRE_DAYS" -> timeToHireDays();
            case "PAYROLL_COST_MONTHLY" -> payrollCostMonthly();
            case "COMPLIANCE_DEADLINES_MET" -> complianceDeadlinesMet();
            default -> null;
        };
    }

    /**
     * Compute values for multiple KPI codes.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> values(List<String> codes) {
        Map<String, Object> result = new HashMap<>();
        for (String code : codes) {
            result.put(code, value(code));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // KPI implementations (tenant-filtered)
    // ─────────────────────────────────────────────────────────────────────

    private Long headcountActive() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM core_hr.employee " +
                "WHERE tenant_id = :tenant AND employment_status = 'ACTIVE'",
                new MapSqlParameterSource("tenant", TENANT),
                Long.class);
    }

    private BigDecimal turnover12M() {
        // Turnover rate = (terminated in last 12 months) / (avg headcount) × 100
        Long terminated = jdbc.queryForObject(
                "SELECT count(*) FROM lifecycle.termination_request " +
                "WHERE tenant_id = :tenant AND status = 'PROCESSED' " +
                "AND effective_date >= current_date - interval '12 months'",
                new MapSqlParameterSource("tenant", TENANT),
                Long.class);

        Long avgHeadcount = jdbc.queryForObject(
                "SELECT count(*) FROM core_hr.employee " +
                "WHERE tenant_id = :tenant AND employment_status IN ('ACTIVE', 'ON_LEAVE')",
                new MapSqlParameterSource("tenant", TENANT),
                Long.class);

        if (avgHeadcount == null || avgHeadcount == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(terminated == null ? 0 : terminated)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(avgHeadcount), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal absenceRate() {
        // Simplified: unplanned leave days / total scheduled days in last 3 months
        // Use attendance.leave_request with status APPROVED, type not PLANNED (if type exists)
        // For simplicity, count leave days / (employees × working days)
        Long leaveDays = jdbc.queryForObject(
                "SELECT COALESCE(SUM(approved_days), 0) FROM attendance.leave_request " +
                "WHERE tenant_id = :tenant AND status = 'APPROVED' " +
                "AND start_date >= current_date - interval '3 months'",
                new MapSqlParameterSource("tenant", TENANT),
                Long.class);

        Long employees = headcountActive();
        if (employees == null || employees == 0) return BigDecimal.ZERO;

        // Rough: ~60 working days per employee in 3 months
        long scheduledDays = employees * 60;
        return BigDecimal.valueOf(leaveDays == null ? 0 : leaveDays)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(scheduledDays), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal avgTenureYears() {
        // Average years since hire_date for active employees
        List<LocalDate> hireDates = jdbc.query(
                "SELECT hire_date FROM core_hr.employee " +
                "WHERE tenant_id = :tenant AND employment_status = 'ACTIVE' AND hire_date IS NOT NULL",
                new MapSqlParameterSource("tenant", TENANT),
                (rs, i) -> rs.getObject("hire_date", LocalDate.class));

        if (hireDates.isEmpty()) return BigDecimal.ZERO;

        LocalDate now = LocalDate.now();
        double avgDays = hireDates.stream()
                .mapToLong(hd -> ChronoUnit.DAYS.between(hd, now))
                .average()
                .orElse(0.0);

        return BigDecimal.valueOf(avgDays / 365.25).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal trainingCompletion() {
        // Mandatory course completion rate: passed / total mandatory enrollments
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM learning.enrollment " +
                "WHERE tenant_id = :tenant AND enrolled_via = 'REQUIRED'",
                new MapSqlParameterSource("tenant", TENANT),
                Long.class);

        Long passed = jdbc.queryForObject(
                "SELECT count(*) FROM learning.enrollment " +
                "WHERE tenant_id = :tenant AND enrolled_via = 'REQUIRED' AND status = 'PASSED'",
                new MapSqlParameterSource("tenant", TENANT),
                Long.class);

        if (total == null || total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(passed == null ? 0 : passed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private Integer enps() {
        // Latest eNPS from most recent pulse survey campaign with RATING_1_10 questions
        // Use SurveyAggregator to compute it
        Integer score = jdbc.queryForObject(
                "SELECT sa.enps FROM engagement.survey_campaign sc " +
                "JOIN engagement.survey_aggregator sa ON sa.campaign_id = sc.id " +
                "WHERE sc.tenant_id = :tenant AND sa.enps IS NOT NULL " +
                "ORDER BY sc.closes_on DESC LIMIT 1",
                new MapSqlParameterSource("tenant", TENANT),
                Integer.class);

        return score != null ? score : 0;
    }

    private Long openPositions() {
        // Active job postings with no hire (status = OPEN or ACTIVE, check recruitment schema)
        return jdbc.queryForObject(
                "SELECT count(*) FROM recruitment.job_posting " +
                "WHERE tenant_id = :tenant AND status IN ('OPEN', 'ACTIVE')",
                new MapSqlParameterSource("tenant", TENANT),
                Long.class);
    }

    private BigDecimal timeToHireDays() {
        // Median days from posting created to offer accepted in last 6 months
        List<Long> days = jdbc.query(
                "SELECT EXTRACT(EPOCH FROM (a.hired_at - jp.created_at))/(24*3600) AS days " +
                "FROM recruitment.application a " +
                "JOIN recruitment.job_posting jp ON a.job_posting_id = jp.id " +
                "WHERE a.tenant_id = :tenant AND a.stage = 'HIRED' " +
                "AND a.hired_at >= current_date - interval '6 months'",
                new MapSqlParameterSource("tenant", TENANT),
                (rs, i) -> rs.getLong("days"));

        if (days.isEmpty()) return BigDecimal.ZERO;
        days.sort(Long::compareTo);
        long median = days.get(days.size() / 2);
        return BigDecimal.valueOf(median);
    }

    private BigDecimal payrollCostMonthly() {
        // Total payroll cost for current month (or latest finalized run)
        // NOTE: payroll_result is partitioned and has NO tenant_id column.
        // Filter via EXISTS on the parent payroll_run which does have tenant_id.
        YearMonth current = YearMonth.now();
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
                        .addValue("tenant", TENANT)
                        .addValue("year", current.getYear())
                        .addValue("month", current.getMonthValue()),
                BigDecimal.class);

        return total != null ? total : BigDecimal.ZERO;
    }

    private BigDecimal complianceDeadlinesMet() {
        // Percentage of compliance deadlines met on time (submitted before/on due_date)
        // Assuming compliance.compliance_deadline or statutory_report_submission exists
        // For now, return a placeholder
        return BigDecimal.valueOf(100); // TODO: implement when compliance schema is available
    }
}
