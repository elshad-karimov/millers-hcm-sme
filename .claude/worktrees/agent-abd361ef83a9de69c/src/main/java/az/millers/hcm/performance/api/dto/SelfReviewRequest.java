package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public record SelfReviewRequest(
        @NotNull BigDecimal rating,
        String comments) {
}
