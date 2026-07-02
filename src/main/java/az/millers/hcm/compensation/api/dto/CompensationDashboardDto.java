package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * M370 — Compensation dashboard aggregations.
 */
public record CompensationDashboardDto(
    int totalEmployeesWithCompRecord,
    int employeesWithoutGrade,
    int employeesWithoutBand,
    int employeesOutOfBand,
    int pendingSalaryChangeApprovals,
    int openExceptions,
    Map<String, Integer> compaRatioDistribution,
    List<BudgetUtilizationRow> budgetUtilization
) {
    public record BudgetUtilizationRow(
        String budgetType,
        String scopeType,
        String scopeRef,
        BigDecimal amount,
        BigDecimal consumed,
        BigDecimal remaining,
        BigDecimal utilizationPct
    ) {}
}
