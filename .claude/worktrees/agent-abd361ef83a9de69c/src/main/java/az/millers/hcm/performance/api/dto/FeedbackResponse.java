package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.performance.domain.Feedback;
import az.millers.hcm.performance.domain.FeedbackRelationship;
import az.millers.hcm.performance.domain.FeedbackStatus;
import az.millers.hcm.performance.domain.FeedbackVisibility;

public record FeedbackResponse(
        UUID id,
        UUID cycleId,
        UUID subjectEmployeeId,
        UUID authorEmployeeId,
        FeedbackRelationship relationship,
        FeedbackVisibility visibility,
        BigDecimal overallRating,
        String strengths,
        String improvements,
        String comments,
        Map<String, Object> competencies,
        FeedbackStatus status,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        String createdBy) {

    public static FeedbackResponse from(Feedback f) {
        return new FeedbackResponse(
                f.getId(), f.getCycleId(), f.getSubjectEmployeeId(),
                // Anonymise the author in the wire form.
                f.getVisibility() == FeedbackVisibility.ANONYMOUS ? null : f.getAuthorEmployeeId(),
                f.getRelationship(), f.getVisibility(),
                f.getOverallRating(), f.getStrengths(), f.getImprovements(), f.getComments(),
                f.getCompetencies(), f.getStatus(), f.getSubmittedAt(),
                f.getCreatedAt(),
                f.getVisibility() == FeedbackVisibility.ANONYMOUS ? "anonymous" : f.getCreatedBy());
    }
}
