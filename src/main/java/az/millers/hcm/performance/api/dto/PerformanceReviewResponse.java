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
        /** M394 — §18.2 section scores + weighted overall + §18.3 band. */
        BigDecimal kpiScore,
        BigDecimal competencyScore,
        BigDecimal valuesScore,
        BigDecimal overallScore,
        String overallBand,
        /** M394 — §18.4 override trail (original preserved). */
        BigDecimal originalRating,
        String overrideReason,
        String overriddenBy,
        OffsetDateTime overriddenAt,
        String recommendation,
        BigDecimal bonusPercent,
        String note,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime closedAt) {

    /**
     * M397 — §19.3/§31.3: calibration notes, potential notes and the override
     * reason are HR/committee-facing; employees viewing their own review get
     * them masked.
     */
    public static PerformanceReviewResponse from(PerformanceReview r, boolean hideCalibration) {
        PerformanceReviewResponse full = from(r);
        if (!hideCalibration) return full;
        return new PerformanceReviewResponse(
                full.id(), full.reviewNo(), full.cycleId(), full.employeeId(), full.managerId(),
                full.status(), full.workflowInstanceId(),
                full.selfRating(), full.selfComments(), full.selfSubmittedAt(),
                full.managerRating(), full.managerComments(), full.managerSubmittedAt(),
                full.finalRating(), full.finalBand(), null /* calibrationNotes */,
                full.goalScore(),
                full.kpiScore(), full.competencyScore(), full.valuesScore(),
                full.overallScore(), full.overallBand(),
                full.originalRating(), null /* overrideReason */, full.overriddenBy(), full.overriddenAt(),
                full.recommendation(), full.bonusPercent(), full.note(),
                full.createdAt(), full.createdBy(), full.closedAt());
    }

    public static PerformanceReviewResponse from(PerformanceReview r) {
        return new PerformanceReviewResponse(
                r.getId(), r.getReviewNo(), r.getCycleId(), r.getEmployeeId(), r.getManagerId(),
                r.getStatus(), r.getWorkflowInstanceId(),
                r.getSelfRating(), r.getSelfComments(), r.getSelfSubmittedAt(),
                r.getManagerRating(), r.getManagerComments(), r.getManagerSubmittedAt(),
                r.getFinalRating(), r.getFinalBand(), r.getCalibrationNotes(),
                r.getGoalScore(),
                r.getKpiScore(), r.getCompetencyScore(), r.getValuesScore(),
                r.getOverallScore(), r.getOverallBand(),
                r.getOriginalRating(), r.getOverrideReason(), r.getOverriddenBy(), r.getOverriddenAt(),
                r.getRecommendation(), r.getBonusPercent(), r.getNote(),
                r.getCreatedAt(), r.getCreatedBy(), r.getClosedAt());
    }
}
