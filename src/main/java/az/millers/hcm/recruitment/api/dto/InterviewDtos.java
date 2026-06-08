package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.recruitment.domain.Interview;
import az.millers.hcm.recruitment.domain.InterviewKit;
import az.millers.hcm.recruitment.domain.InterviewQuestion;
import az.millers.hcm.recruitment.domain.InterviewRecommendation;
import az.millers.hcm.recruitment.domain.InterviewScore;
import az.millers.hcm.recruitment.domain.InterviewStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Recruitment M85 — DTOs for interview kits, questions, interviews and
 * per-question scores. Records only; all flat shapes the SPA consumes
 * directly.
 */
public final class InterviewDtos {
    private InterviewDtos() {}

    // ── Kit ──────────────────────────────────────────────────────────────────

    public record KitRequest(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "^[A-Z0-9_-]+$", message = "code must be uppercase alphanumeric")
            String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 4000) String description,
            UUID jobFamilyId,
            Boolean active) {}

    public record KitResponse(
            UUID id, String code, String name, String description,
            UUID jobFamilyId, boolean active,
            OffsetDateTime createdAt, String createdBy,
            OffsetDateTime updatedAt, String updatedBy) {

        public static KitResponse from(InterviewKit k) {
            return new KitResponse(
                    k.getId(), k.getCode(), k.getName(), k.getDescription(),
                    k.getJobFamilyId(), k.isActive(),
                    k.getCreatedAt(), k.getCreatedBy(),
                    k.getUpdatedAt(), k.getUpdatedBy());
        }
    }

    // ── Question ────────────────────────────────────────────────────────────

    public record QuestionRequest(
            @NotBlank @Size(max = 4000) String questionText,
            @Min(1) @Max(10) Integer weight,
            Integer sortOrder,
            Boolean required,
            Boolean active) {}

    public record QuestionResponse(
            UUID id, UUID kitId, String questionText,
            int weight, int sortOrder, boolean required, boolean active) {

        public static QuestionResponse from(InterviewQuestion q) {
            return new QuestionResponse(
                    q.getId(), q.getKitId(), q.getQuestionText(),
                    q.getWeight(), q.getSortOrder(), q.isRequired(), q.isActive());
        }
    }

    // ── Interview ───────────────────────────────────────────────────────────

    public record InterviewSchedule(
            @NotNull UUID applicationId,
            @NotNull UUID kitId,
            @NotNull UUID interviewerEmployeeId,
            @NotNull OffsetDateTime scheduledAt) {}

    public record InterviewReschedule(
            @NotNull OffsetDateTime scheduledAt,
            UUID interviewerEmployeeId,
            UUID kitId) {}

    public record InterviewFinalize(
            @NotNull InterviewRecommendation recommendation,
            @Size(max = 4000) String overallComment) {}

    public record ScoreUpsert(
            @NotNull UUID questionId,
            @Min(1) @Max(5) int score,
            @Size(max = 4000) String comment) {}

    public record InterviewResponse(
            UUID id, String interviewNo,
            UUID applicationId, UUID kitId,
            UUID interviewerEmployeeId,
            OffsetDateTime scheduledAt,
            InterviewStatus status,
            BigDecimal overallScore,
            InterviewRecommendation recommendation,
            String overallComment,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt, String createdBy,
            OffsetDateTime updatedAt, String updatedBy) {

        public static InterviewResponse from(Interview i) {
            return new InterviewResponse(
                    i.getId(), i.getInterviewNo(),
                    i.getApplicationId(), i.getKitId(),
                    i.getInterviewerEmployeeId(),
                    i.getScheduledAt(), i.getStatus(),
                    i.getOverallScore(), i.getRecommendation(),
                    i.getOverallComment(), i.getCompletedAt(),
                    i.getCreatedAt(), i.getCreatedBy(),
                    i.getUpdatedAt(), i.getUpdatedBy());
        }
    }

    public record ScoreResponse(
            UUID id, UUID interviewId, UUID questionId,
            int score, String comment,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {

        public static ScoreResponse from(InterviewScore s) {
            return new ScoreResponse(
                    s.getId(), s.getInterviewId(), s.getQuestionId(),
                    s.getScore(), s.getComment(),
                    s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    /** Composite shape — interview + its kit's question list + current scores. */
    public record InterviewDetail(
            InterviewResponse interview,
            KitResponse kit,
            List<QuestionResponse> questions,
            List<ScoreResponse> scores) {}
}
