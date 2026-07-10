package az.millers.hcm.engagement.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * M481: Fulfill redemption request.
 */
public record FulfillRedemptionRequest(
    @NotNull UUID payrollRunId
) {}
