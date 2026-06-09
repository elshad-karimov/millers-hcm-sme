package az.millers.hcm.reporting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.reporting.api.dto.TerminationReportDtos.DepartmentTurnoverReport;
import az.millers.hcm.reporting.api.dto.TerminationReportDtos.DepartmentTurnoverRow;
import az.millers.hcm.reporting.api.dto.TerminationReportDtos.ExitInterviewAnalysisReport;
import az.millers.hcm.reporting.api.dto.TerminationReportDtos.ReasonLeaving;
import az.millers.hcm.reporting.api.dto.TerminationReportDtos.ReasonRow;
import az.millers.hcm.reporting.api.dto.TerminationReportDtos.TerminationByReasonReport;

/**
 * Termination-module report computations (M226 / PRD §8.11.5).
 */
@Service
public class TerminationReportService {

    private final NamedParameterJdbcTemplate jdbc;

    public TerminationReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── 1. Termination by reason ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public TerminationByReasonReport byReason(int year) {
        String sql = """
                SELECT reason_code, COUNT(*) AS cnt
                FROM lifecycle.termination_request
                WHERE status IN ('PROCESSED','APPROVED')
                  AND EXTRACT(YEAR FROM effective_date) = :year
                GROUP BY reason_code
                ORDER BY cnt DESC
                """;

        record Raw(String code, long count) {}
        List<Raw> raw = jdbc.query(sql, new MapSqlParameterSource("year", year),
                (rs, i) -> new Raw(rs.getString("reason_code"), rs.getLong("cnt")));

        long total = raw.stream().mapToLong(Raw::count).sum();
        List<ReasonRow> rows = raw.stream()
                .map(r -> new ReasonRow(r.code(), r.count(),
                        total > 0
                                ? BigDecimal.valueOf(r.count() * 100.0 / total)
                                           .setScale(1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO))
                .toList();

        return new TerminationByReasonReport(year, total, rows);
    }

    // ── 2. Department turnover ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DepartmentTurnoverReport departmentTurnover(int year) {
        // Count terminations per org-unit (using employee's org unit at query time —
        // best available proxy for the unit at time of departure).
        String sql = """
                WITH terms AS (
                    SELECT e.org_unit_id, COUNT(*) AS term_count
                    FROM lifecycle.termination_request t
                    JOIN core_hr.employee e ON e.id = t.employee_id
                    WHERE t.status IN ('PROCESSED','APPROVED')
                      AND EXTRACT(YEAR FROM t.effective_date) = :year
                      AND e.org_unit_id IS NOT NULL
                    GROUP BY e.org_unit_id
                ),
                active AS (
                    SELECT org_unit_id, COUNT(*) AS active_count
                    FROM core_hr.employee
                    WHERE employment_status = 'ACTIVE'
                      AND org_unit_id IS NOT NULL
                    GROUP BY org_unit_id
                )
                SELECT u.code, u.name, u.unit_type,
                       COALESCE(t.term_count, 0)   AS terminations,
                       COALESCE(a.active_count, 0)  AS active_headcount
                FROM organization.org_unit u
                JOIN terms t ON t.org_unit_id = u.id
                LEFT JOIN active a ON a.org_unit_id = u.id
                ORDER BY t.term_count DESC, u.name
                """;

        List<DepartmentTurnoverRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("year", year),
                (rs, i) -> {
                    long terms  = rs.getLong("terminations");
                    long active = rs.getLong("active_headcount");
                    long denom  = active + terms;
                    BigDecimal rate = denom > 0
                            ? BigDecimal.valueOf(terms * 100.0 / denom)
                                       .setScale(1, RoundingMode.HALF_UP)
                            : null;
                    return new DepartmentTurnoverRow(
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("unit_type"),
                            terms, active, rate);
                });

        long total = rows.stream().mapToLong(DepartmentTurnoverRow::terminations).sum();
        return new DepartmentTurnoverReport(year, total, rows);
    }

    // ── 3. Exit-interview analysis ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public ExitInterviewAnalysisReport exitInterviewAnalysis(int year) {
        // Header aggregates
        String aggSql = """
                SELECT COUNT(*)                                    AS total,
                       AVG(ei.overall_rating::numeric)             AS avg_rating,
                       COUNT(*) FILTER (WHERE ei.would_recommend)  AS recommend_yes,
                       COUNT(*) FILTER (WHERE ei.would_recommend IS NOT NULL) AS recommend_answered
                FROM lifecycle.exit_interview ei
                JOIN lifecycle.termination_request t ON t.id = ei.termination_id
                WHERE EXTRACT(YEAR FROM t.effective_date) = :year
                """;

        record Agg(int total, BigDecimal avgRating, long yes, long answered) {}
        List<Agg> aggRows = jdbc.query(aggSql, new MapSqlParameterSource("year", year),
                (rs, i) -> new Agg(
                        rs.getInt("total"),
                        rs.getBigDecimal("avg_rating"),
                        rs.getLong("recommend_yes"),
                        rs.getLong("recommend_answered")));

        Agg agg = aggRows.isEmpty() ? new Agg(0, null, 0, 0) : aggRows.get(0);

        BigDecimal avgRating = agg.avgRating() != null
                ? agg.avgRating().setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal recommendPct = agg.answered() > 0
                ? BigDecimal.valueOf(agg.yes() * 100.0 / agg.answered())
                            .setScale(1, RoundingMode.HALF_UP)
                : null;

        // Reason-for-leaving breakdown (free-text; group by trimmed value).
        String reasonSql = """
                SELECT COALESCE(TRIM(ei.reason_for_leaving), '(not provided)') AS reason,
                       COUNT(*) AS cnt
                FROM lifecycle.exit_interview ei
                JOIN lifecycle.termination_request t ON t.id = ei.termination_id
                WHERE EXTRACT(YEAR FROM t.effective_date) = :year
                GROUP BY TRIM(ei.reason_for_leaving)
                ORDER BY cnt DESC
                """;

        record RawReason(String reason, long count) {}
        List<RawReason> rawReasons = jdbc.query(reasonSql,
                new MapSqlParameterSource("year", year),
                (rs, i) -> new RawReason(rs.getString("reason"), rs.getLong("cnt")));

        long reasonTotal = rawReasons.stream().mapToLong(RawReason::count).sum();
        List<ReasonLeaving> reasons = new ArrayList<>(rawReasons.size());
        for (RawReason r : rawReasons) {
            BigDecimal share = reasonTotal > 0
                    ? BigDecimal.valueOf(r.count() * 100.0 / reasonTotal)
                               .setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            reasons.add(new ReasonLeaving(r.reason(), r.count(), share));
        }

        return new ExitInterviewAnalysisReport(
                year, agg.total(), avgRating, recommendPct, reasons);
    }
}
