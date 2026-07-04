package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.compensation.domain.BudgetScopeType;
import az.millers.hcm.compensation.domain.BudgetType;
import az.millers.hcm.compensation.domain.CompensationBudget;

public record CompensationBudgetResponse(
        UUID id,
        BudgetScopeType scopeType,
        String scopeRef,
        BudgetType budgetType,
        BigDecimal amount,
        String currency,
        BigDecimal consumedAmount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean isActive
) {
    public static CompensationBudgetResponse from(CompensationBudget b) {
        return new CompensationBudgetResponse(
                b.getId(),
                b.getScopeType(),
                b.getScopeRef(),
                b.getBudgetType(),
                b.getAmount(),
                b.getCurrency(),
                b.getConsumedAmount(),
                b.getEffectiveFrom(),
                b.getEffectiveTo(),
                b.getIsActive()
        );
    }
}
