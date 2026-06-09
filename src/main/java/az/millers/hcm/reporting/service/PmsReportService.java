package az.millers.hcm.reporting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.reporting.api.dto.PmsReportDtos.DepartmentKpiReport;
import az.millers.hcm.reporting.api.dto.PmsReportDtos.DeptKpiRow;
import az.millers.hcm.reporting.api.dto.PmsReportDtos.GoalCompletionReport;
import az.millers.hcm.reporting.api.dto.PmsReportDtos.GoalStatusCount;
import az.millers.hcm.reporting.api.dto.PmsReportDtos.PerformerReport;
import az.millers.hcm.reporting.api.dto.PmsReportDtos.PerformerRow;

/**
 * PMS report computations (M226 / PRD §8.13.11).
 */
@Service
public class PmsReportService {

    private final NamedParameterJdbcTemplate jdbc;

    public PmsReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── 1. Department KPI report ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public DepartmentKpiReport departmentKpi(UUID cycleId) {
        String sql = """
                SELECT u.code AS org_unit_code,
                       u.name AS org_unit_name,
                       COUNT(*)                                        AS total,
                       COUNT(*) FILTER (WHERE g.status = 'ACHIEVED')  AS achieved,
                       COUNT(*) FILTER (WHERE g.status = 'AT_RISK')   AS at_risk,
                       COUNT(*) FILTER (WHERE g.status = 'MISSED')    AS missed,
                       AVG(g.progress_percent)                         AS avg_progress
                FROM performance.goal g
                JOIN core_hr.employee e ON e.id = g.employee_id
                JOIN organization.org_unit u ON u.id = e.org_unit_id
                WHERE g.cycle_id = :cycleId
                  AND g.status NOT IN ('DRAFT','CANCELLED')
                  AND e.org_unit_id IS NOT NULL
                GROUP BY u.id, u.code, u.name
                ORDER BY u.name
                """;

        List<DeptKpiRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("cycleId", cycleId),
                (rs, i) -> {
                    long total    = rs.getLong("total");
                    long achieved = rs.getLong("achieved");
                    BigDecimal avgProgress = rs.getBigDecimal("avg_progress");
                    BigDecimal completionRate = total > 0
                            ? BigDecimal.valueOf(achieved * 100.0 / total)
                                       .setScale(1, RoundingMode.HALF_UP)
                            : null;
                    return new DeptKpiRow(
                            rs.getString("org_unit_code"),
                            rs.getString("org_unit_name"),
                            (int) total,
                            (int) achieved,
                            (int) rs.getLong("at_risk"),
                            (int) rs.getLong("missed"),
                            avgProgress != null
                                    ? avgProgress.setScale(1, RoundingMode.HALF_UP)
                                    : BigDecimal.ZERO,
                            completionRate);
                });

        int totalGoals = rows.stream().mapToInt(DeptKpiRow::totalGoals).sum();
        return new DepartmentKpiReport(cycleId, totalGoals, rows);
    }

    // ── 2. Goal-completion report ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public GoalCompletionReport goalCompletion(UUID cycleId) {
        String sql = """
                SELECT status, COUNT(*) AS cnt,
                       AVG(progress_percent) AS avg_progress
                FROM performance.goal
                WHERE cycle_id = :cycleId
                  AND status != 'DRAFT'
                GROUP BY status
                ORDER BY cnt DESC
                """;

        record RawStatus(String status, long count, BigDecimal avgProgress) {}
        List<RawStatus> raw = jdbc.query(sql,
                new MapSqlParameterSource("cycleId", cycleId),
                (rs, i) -> new RawStatus(
                        rs.getString("status"),
                        rs.getLong("cnt"),
                        rs.getBigDecimal("avg_progress")));

        long total = raw.stream().mapToLong(RawStatus::count).sum();

        // Overall avg progress (weighted by count is same as simple average here).
        BigDecimal avgProgress = BigDecimal.ZERO;
        if (!raw.isEmpty()) {
            String avgSql = "SELECT AVG(progress_percent) FROM performance.goal"
                    + " WHERE cycle_id = :cycleId AND status != 'DRAFT'";
            BigDecimal v = jdbc.queryForObject(avgSql,
                    new MapSqlParameterSource("cycleId", cycleId), BigDecimal.class);
            avgProgress = v != null ? v.setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }

        long achieved = raw.stream()
                .filter(r -> "ACHIEVED".equals(r.status()))
                .mapToLong(RawStatus::count).sum();
        BigDecimal completionRate = total > 0
                ? BigDecimal.valueOf(achieved * 100.0 / total)
                           .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<GoalStatusCount> byStatus = new ArrayList<>(raw.size());
        for (RawStatus r : raw) {
            BigDecimal share = total > 0
                    ? BigDecimal.valueOf(r.count() * 100.0 / total)
                               .setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            byStatus.add(new GoalStatusCount(r.status(), r.count(), share));
        }

        return new GoalCompletionReport(cycleId, (int) total, avgProgress,
                completionRate, byStatus);
    }

    // ── 3 & 4. High- / Low-performer reports ──────────────────────────────

    @Transactional(readOnly = true)
    public PerformerReport highPerformers(UUID cycleId, BigDecimal minRating) {
        return performers(cycleId, minRating, true);
    }

    @Transactional(readOnly = true)
    public PerformerReport lowPerformers(UUID cycleId, BigDecimal maxRating) {
        return performers(cycleId, maxRating, false);
    }

    private PerformerReport performers(UUID cycleId, BigDecimal threshold, boolean high) {
        String comparison = high ? ">=" : "<=";
        String order      = high ? "DESC" : "ASC";

        String sql = """
                SELECT pr.employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       COALESCE(u.name, '') AS org_unit_name,
                       pr.final_rating,
                       pr.final_band,
                       pr.self_rating,
                       pr.manager_rating
                FROM performance.performance_review pr
                JOIN core_hr.employee e ON e.id = pr.employee_id
                LEFT JOIN organization.org_unit u ON u.id = e.org_unit_id
                WHERE pr.cycle_id = :cycleId
                  AND pr.status IN ('COMPLETED','APPROVED')
                  AND pr.final_rating """ + comparison + " :threshold"
                + " ORDER BY pr.final_rating " + order + ", e.employee_no";

        List<PerformerRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("cycleId", cycleId)
                        .addValue("threshold", threshold),
                (rs, i) -> new PerformerRow(
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getString("org_unit_name"),
                        rs.getBigDecimal("final_rating"),
                        rs.getString("final_band"),
                        rs.getBigDecimal("self_rating"),
                        rs.getBigDecimal("manager_rating")));

        BigDecimal avg = rows.isEmpty() ? null
                : rows.stream()
                      .map(PerformerRow::finalRating)
                      .filter(r -> r != null)
                      .reduce(BigDecimal.ZERO, BigDecimal::add)
                      .divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);

        return new PerformerReport(cycleId, threshold, rows.size(), avg, rows);
    }
}
