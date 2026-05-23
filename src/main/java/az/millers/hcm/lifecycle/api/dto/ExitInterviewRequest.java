package az.millers.hcm.lifecycle.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ExitInterviewRequest(
        @Min(1) @Max(5) Integer overallRating,
        Boolean wouldRecommend,
        String reasonForLeaving,
        String feedback,
        String improvementSuggestions) {
}
