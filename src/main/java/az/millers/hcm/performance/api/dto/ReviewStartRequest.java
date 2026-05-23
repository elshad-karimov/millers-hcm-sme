package az.millers.hcm.performance.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ReviewStartRequest(
        @NotNull UUID cycleId,
        @NotNull UUID employeeId,
        UUID managerId) {
}
