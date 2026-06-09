package az.millers.hcm.reporting.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs for the three termination-module reports mandated by PRD §8.11.5
 * (M226).
 */
public final class TerminationReportDtos {

    private TerminationReportDtos() {}

    // ── Termination by reason ──────────────────────────────────────────────

    public record ReasonRow(
            String reasonCode,
            long count,
            /** count / total × 100 */
            BigDecimal sharePct) {}

    public record TerminationByReasonReport(
            int year,
            long total,
            List<ReasonRow> rows) {}

    // ── Department turnover ────────────────────────────────────────────────

    public record DepartmentTurnoverRow(
            String orgUnitCode,
            String orgUnitName,
            String unitType,
            long terminations,
            long activeHeadcount,
            /** terminations / (active + terminations) × 100; null when denominator is zero. */
            BigDecimal turnoverRatePct) {}

    public record DepartmentTurnoverReport(
            int year,
            long totalTerminations,
            List<DepartmentTurnoverRow> rows) {}

    // ── Exit-interview analysis ────────────────────────────────────────────

    public record ReasonLeaving(
            String reason,
            long count,
            BigDecimal sharePct) {}

    public record ExitInterviewAnalysisReport(
            int year,
            int totalInterviews,
            /** Average overall rating (1–5); null if no rated interviews. */
            BigDecimal avgOverallRating,
            /** Percentage of respondents who answered wouldRecommend = true. */
            BigDecimal wouldRecommendPct,
            List<ReasonLeaving> reasonBreakdown) {}
}
