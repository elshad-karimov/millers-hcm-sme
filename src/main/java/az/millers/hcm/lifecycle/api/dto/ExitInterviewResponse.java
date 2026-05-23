package az.millers.hcm.lifecycle.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ExitInterview;

public record ExitInterviewResponse(
        UUID id,
        UUID terminationId,
        OffsetDateTime conductedAt,
        String conductedBy,
        Integer overallRating,
        Boolean wouldRecommend,
        String reasonForLeaving,
        String feedback,
        String improvementSuggestions) {

    public static ExitInterviewResponse from(ExitInterview e) {
        return new ExitInterviewResponse(
                e.getId(),
                e.getTerminationId(),
                e.getConductedAt(),
                e.getConductedBy(),
                e.getOverallRating(),
                e.getWouldRecommend(),
                e.getReasonForLeaving(),
                e.getFeedback(),
                e.getImprovementSuggestions());
    }
}
