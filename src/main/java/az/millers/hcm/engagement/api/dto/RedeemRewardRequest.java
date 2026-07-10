package az.millers.hcm.engagement.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * M481: Redeem catalog item.
 */
public record RedeemRewardRequest(
    @NotNull UUID catalogItemId,
    String deliveryAddress
) {}
