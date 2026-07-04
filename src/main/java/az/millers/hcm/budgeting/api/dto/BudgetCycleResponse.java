package az.millers.hcm.budgeting.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.budgeting.domain.BudgetCycle;
import az.millers.hcm.budgeting.domain.BudgetCycleStatus;
import az.millers.hcm.budgeting.domain.CycleType;

public record BudgetCycleResponse(
        UUID id,
        String code,
        String name,
        CycleType cycleType,
        LocalDate periodStart,
        LocalDate periodEnd,
        BudgetCycleStatus status,
        LocalDate submissionDeadline
) {
    public static BudgetCycleResponse from(BudgetCycle c) {
        return new BudgetCycleResponse(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getCycleType(),
                c.getPeriodStart(),
                c.getPeriodEnd(),
                c.getStatus(),
                c.getSubmissionDeadline()
        );
    }
}
