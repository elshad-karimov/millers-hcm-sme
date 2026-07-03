package az.millers.hcm.performance.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** M392 — submit an employee's DRAFT goal plan for a cycle for manager approval. */
public record GoalPlanSubmitRequest(
        @NotNull UUID cycleId,
        @NotNull UUID employeeId) {
}
