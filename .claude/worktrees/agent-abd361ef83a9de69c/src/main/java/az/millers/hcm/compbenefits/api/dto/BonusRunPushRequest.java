package az.millers.hcm.compbenefits.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record BonusRunPushRequest(
        @NotNull UUID targetPayrollRunId) {
}
