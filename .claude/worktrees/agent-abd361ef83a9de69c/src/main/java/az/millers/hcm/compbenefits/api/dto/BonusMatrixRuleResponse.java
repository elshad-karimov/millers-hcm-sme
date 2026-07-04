package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.BonusMatrixRule;

public record BonusMatrixRuleResponse(
        UUID id,
        String code,
        String description,
        String matchRecommendation,
        BigDecimal minRating,
        BigDecimal maxRating,
        BigDecimal bonusPercent,
        BigDecimal flatAmount,
        String currency,
        BigDecimal maxAmount,
        int priority,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy) {

    public static BonusMatrixRuleResponse from(BonusMatrixRule r) {
        return new BonusMatrixRuleResponse(
                r.getId(), r.getCode(), r.getDescription(),
                r.getMatchRecommendation(), r.getMinRating(), r.getMaxRating(),
                r.getBonusPercent(), r.getFlatAmount(), r.getCurrency(),
                r.getMaxAmount(), r.getPriority(),
                r.getEffectiveFrom(), r.getEffectiveTo(), r.isActive(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getCreatedBy(), r.getUpdatedBy());
    }
}
