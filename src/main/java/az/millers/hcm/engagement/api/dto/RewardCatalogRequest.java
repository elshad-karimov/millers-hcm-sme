package az.millers.hcm.engagement.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * M481: Reward catalog item request.
 */
public record RewardCatalogRequest(
    @NotBlank String code,
    @NotBlank String name,
    String description,
    @NotNull @Min(1) Integer points,
    BigDecimal monetaryValue,
    String category,
    Boolean taxable,
    Boolean active
) {}
