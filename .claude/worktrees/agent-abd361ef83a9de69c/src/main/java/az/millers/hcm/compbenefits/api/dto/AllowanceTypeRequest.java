package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;

import az.millers.hcm.compbenefits.domain.AllowanceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AllowanceTypeRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotNull AllowanceCategory category,
        Boolean taxable,
        Boolean recurring,
        BigDecimal defaultAmount,
        String currency,
        Boolean active) {
}
