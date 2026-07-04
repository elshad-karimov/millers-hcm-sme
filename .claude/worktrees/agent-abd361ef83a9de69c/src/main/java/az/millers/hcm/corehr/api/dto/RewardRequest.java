package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import az.millers.hcm.corehr.domain.RewardType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RewardRequest(
        @NotNull RewardType rewardType,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 8000) String description,
        @DecimalMin("0.0") BigDecimal awardValue,
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code")
        String currency,
        @NotNull LocalDate awardedAt,
        @Size(max = 500) String certificateUrl,
        @Size(max = 4000) String notes) {
}
