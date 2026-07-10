package az.millers.hcm.engagement.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * M481: Reward budget request.
 */
public record BudgetRequest(
    @NotNull Integer year,
    UUID orgUnitId,
    @NotNull @DecimalMin("0.0") BigDecimal totalAmount
) {}
