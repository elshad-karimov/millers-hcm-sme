package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ExitInterview;

public record ExitInterviewResponse(
        UUID id,
        UUID terminationId,
        UUID caseId,
        OffsetDateTime conductedAt,
        String conductedBy,
        Integer overallRating,
        Boolean wouldRecommend,
        String reasonForLeaving,
        String feedback,
        String improvementSuggestions,
        String interviewMode,
        LocalDate interviewDate,
        Boolean followUpRequired,
        String followUpNotes) {

    public static ExitInterviewResponse from(ExitInterview e) {
        return new ExitInterviewResponse(
                e.getId(),
                e.getTerminationId(),
                e.getCaseId(),
                e.getConductedAt(),
                e.getConductedBy(),
                e.getOverallRating(),
                e.getWouldRecommend(),
                e.getReasonForLeaving(),
                e.getFeedback(),
                e.getImprovementSuggestions(),
                e.getInterviewMode(),
                e.getInterviewDate(),
                e.getFollowUpRequired(),
                e.getFollowUpNotes());
    }
}
