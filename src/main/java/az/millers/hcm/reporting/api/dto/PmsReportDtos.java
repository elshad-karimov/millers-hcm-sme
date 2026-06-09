package az.millers.hcm.reporting.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the four PMS reports mandated by PRD §8.13.11 (M226).
 */
public final class PmsReportDtos {

    private PmsReportDtos() {}

    // ── Department KPI report ─────────────────────────────────────────────

    public record DeptKpiRow(
            String orgUnitCode,
            String orgUnitName,
            int totalGoals,
            int achievedGoals,
            int atRiskGoals,
            int missedGoals,
            /** Average progress across all goals in this unit (0–100). */
            BigDecimal avgProgressPct,
            /** achievedGoals / totalGoals × 100; null when totalGoals is zero. */
            BigDecimal completionRatePct) {}

    public record DepartmentKpiReport(
            UUID cycleId,
            int totalGoals,
            List<DeptKpiRow> rows) {}

    // ── Goal-completion report ─────────────────────────────────────────────

    public record GoalStatusCount(
            String status,
            long count,
            BigDecimal sharePct) {}

    public record GoalCompletionReport(
            UUID cycleId,
            int totalGoals,
            /** Average progress across all goals in the cycle (0–100). */
            BigDecimal avgProgressPct,
            /** ACHIEVED / total × 100 */
            BigDecimal overallCompletionRatePct,
            List<GoalStatusCount> byStatus) {}

    // ── High- / Low-performer reports ─────────────────────────────────────

    public record PerformerRow(
            UUID employeeId,
            String employeeNo,
            String fullName,
            String orgUnitName,
            BigDecimal finalRating,
            String finalBand,
            BigDecimal selfRating,
            BigDecimal managerRating) {}

    public record PerformerReport(
            UUID cycleId,
            /** The threshold used (≥ for high, ≤ for low). */
            BigDecimal threshold,
            int count,
            BigDecimal avgFinalRating,
            List<PerformerRow> rows) {}
}
