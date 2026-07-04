package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.performance.domain.FeedbackRelationship;
import az.millers.hcm.performance.domain.FeedbackVisibility;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
        @NotNull UUID cycleId,
        @NotNull UUID subjectEmployeeId,
        UUID authorEmployeeId,
        @NotNull FeedbackRelationship relationship,
        FeedbackVisibility visibility,
        BigDecimal overallRating,
        String strengths,
        String improvements,
        String comments,
        Map<String, Object> competencies,
        boolean submit) {
}
