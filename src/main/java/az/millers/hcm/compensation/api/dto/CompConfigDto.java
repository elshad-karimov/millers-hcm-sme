package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;

import az.millers.hcm.compensation.domain.BudgetExceededPolicy;
import az.millers.hcm.compensation.domain.CompConfig;

/**
 * M359 — Tenant compensation configuration DTO.
 */
public record CompConfigDto(
    BigDecimal maxIncreasePctWithoutApproval,
    BudgetExceededPolicy budgetExceededPolicy,
    String defaultCurrency
) {
    public static CompConfigDto from(CompConfig entity) {
        if (entity == null) return null;
        return new CompConfigDto(
            entity.getMaxIncreasePctWithoutApproval(),
            entity.getBudgetExceededPolicy(),
            entity.getDefaultCurrency()
        );
    }
}
