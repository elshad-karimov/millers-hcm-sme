package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.domain.ReviewStatus;

public record PerformanceReviewResponse(
        UUID id,
        String reviewNo,
        UUID cycleId,
        UUID employeeId,
        UUID managerId,
        ReviewStatus status,
        UUID workflowInstanceId,
        BigDecimal selfRating,
        String selfComments,
        OffsetDateTime selfSubmittedAt,
        BigDecimal managerRating,
        String managerComments,
        OffsetDateTime managerSubmittedAt,
        BigDecimal finalRating,
        String finalBand,
        String calibrationNotes,
        BigDecimal goalScore,
        String recommendation,
        BigDecimal bonusPercent,
        String note,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime closedAt) {

    public static PerformanceReviewResponse from(PerformanceReview r) {
        return new PerformanceReviewResponse(
                r.getId(), r.getReviewNo(), r.getCycleId(), r.getEmployeeId(), r.getManagerId(),
                r.getStatus(), r.getWorkflowInstanceId(),
                r.getSelfRating(), r.getSelfComments(), r.getSelfSubmittedAt(),
                r.getManagerRating(), r.getManagerComments(), r.getManagerSubmittedAt(),
                r.getFinalRating(), r.getFinalBand(), r.getCalibrationNotes(),
                r.getGoalScore(), r.getRecommendation(), r.getBonusPercent(), r.getNote(),
                r.getCreatedAt(), r.getCreatedBy(), r.getClosedAt());
    }
}
