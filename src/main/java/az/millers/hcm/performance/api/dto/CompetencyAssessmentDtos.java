package az.millers.hcm.performance.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.learning.domain.Competency;
import az.millers.hcm.performance.domain.PerfCompetencyAssessment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs for per-review competency assessments (HCM_12 M393). */
public final class CompetencyAssessmentDtos {

    private CompetencyAssessmentDtos() {}

    /** Manually add one competency to a review (beyond the position seed). */
    public record AddCompetencyRequest(
            @NotNull UUID competencyId,
            @Min(1) @Max(5) Integer requiredLevel) {
    }

    /** Rate one assessment row — any subset of the three levels. */
    public record RateRequest(
            @Min(1) @Max(5) Integer selfLevel,
            @Min(1) @Max(5) Integer managerLevel,
            @Min(1) @Max(5) Integer finalLevel,
            @Size(max = 1000) String comment) {
    }

    public record AssessmentResponse(
            UUID id, UUID reviewId, UUID competencyId, String competencyCode,
            String competencyName, String category, Integer requiredLevel, Integer selfLevel,
            Integer managerLevel, Integer finalLevel, Integer gap, String comment,
            OffsetDateTime updatedAt) {

        public static AssessmentResponse from(PerfCompetencyAssessment a, Competency c) {
            return new AssessmentResponse(a.getId(), a.getReviewId(), a.getCompetencyId(),
                    c == null ? null : c.getCode(), c == null ? null : c.getName(),
                    c == null || c.getCategory() == null ? null : c.getCategory().name(),
                    a.getRequiredLevel(), a.getSelfLevel(),
                    a.getManagerLevel(), a.getFinalLevel(), a.getGap(), a.getComment(),
                    a.getUpdatedAt());
        }
    }
}
