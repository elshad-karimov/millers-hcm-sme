package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** M258 — Position budget-vs-actual variance DTOs (PRD §19). */
public final class PositionVarianceDtos {

    private PositionVarianceDtos() {}

    /**
     * One row of the variance dashboard.
     *
     * <p>{@code budgeted} can be null when the position has actual payroll
     * cost but no budget set yet — surfacing the gap to the planner.
     * {@code variancePct} is null when the budget is zero (no meaningful
     * percentage). {@code status} ∈ {OVER, UNDER, ON_TRACK, NO_BUDGET}
     * for SPA colour coding.
     */
    public record PositionVarianceRow(
            UUID positionId,
            String positionCode,
            String positionTitle,
            String orgUnitLabel,
            int approvedHeadcount,
            int actualHeadcount,
            BigDecimal budgeted,
            BigDecimal actual,
            BigDecimal variance,
            BigDecimal variancePct,
            String currency,
            String status) {}

    public record VarianceTotals(
            BigDecimal totalBudget,
            BigDecimal totalActual,
            BigDecimal totalVariance,
            int overCount,
            int underCount,
            int noBudgetCount) {}

    public record VarianceReport(
            int year,
            int month,
            int rowCount,
            VarianceTotals totals,
            List<PositionVarianceRow> rows) {}
}
