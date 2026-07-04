package az.millers.hcm.budgeting.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * HCM_20 M427 — Budget-vs-actual variance per department.
 */
public record DepartmentVariance(
        UUID orgUnitId,
        BigDecimal budgetAmount,
        BigDecimal actualCost,
        BigDecimal variance,          // budget − actual
        BigDecimal utilizationPct,    // actual / budget × 100
        VarianceStatus status
) {}
