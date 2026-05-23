package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BonusMatrixRuleRequest(
        @NotBlank String code,
        String description,
        String matchRecommendation,
        BigDecimal minRating,
        BigDecimal maxRating,
        BigDecimal bonusPercent,
        BigDecimal flatAmount,
        String currency,
        BigDecimal maxAmount,
        Integer priority,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {
}
