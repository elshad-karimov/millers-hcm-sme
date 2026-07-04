package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.BonusItemSource;
import az.millers.hcm.compbenefits.domain.BonusRunItem;

public record BonusRunItemResponse(
        UUID id,
        String itemNo,
        UUID runId,
        UUID employeeId,
        UUID reviewId,
        String recommendation,
        BigDecimal finalRating,
        BigDecimal baseSalary,
        BigDecimal bonusPercent,
        BigDecimal bonusAmount,
        String currency,
        BonusItemSource source,
        UUID matrixRuleId,
        UUID pushedPayrollBonusId,
        String note,
        OffsetDateTime createdAt) {

    public static BonusRunItemResponse from(BonusRunItem i) {
        return new BonusRunItemResponse(
                i.getId(), i.getItemNo(), i.getRunId(), i.getEmployeeId(),
                i.getReviewId(), i.getRecommendation(), i.getFinalRating(),
                i.getBaseSalary(), i.getBonusPercent(), i.getBonusAmount(),
                i.getCurrency(), i.getSource(), i.getMatrixRuleId(),
                i.getPushedPayrollBonusId(), i.getNote(), i.getCreatedAt());
    }
}
