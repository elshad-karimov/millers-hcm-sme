package az.millers.hcm.engagement.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.engagement.domain.CampaignStatus;
import az.millers.hcm.engagement.domain.QuestionType;
import az.millers.hcm.engagement.domain.SurveyAnswer;
import az.millers.hcm.engagement.domain.SurveyCampaign;
import az.millers.hcm.engagement.domain.SurveyQuestion;
import az.millers.hcm.engagement.domain.SurveyResponse;
import az.millers.hcm.engagement.domain.SurveyTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** DTOs for the M116 engagement surveys surface. */
public final class SurveyDtos {

    private SurveyDtos() {}

    // ── Templates ───────────────────────────────────────────────────────

    public record QuestionRequest(
            @Min(0) int orderIndex,
            @NotBlank String prompt,
            @NotNull QuestionType questionType,
            /** Free-form JSON; e.g. {@code {"options": ["A","B"]}} for MULTIPLE_CHOICE. */
            String metadata,
            Boolean required) {
    }

    public record TemplateRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            Boolean anonymous,
            Boolean active,
            @NotNull @Valid List<QuestionRequest> questions) {
    }

    public record QuestionResponse(
            UUID id,
            int orderIndex,
            String prompt,
            QuestionType questionType,
            String metadata,
            boolean required) {

        public static QuestionResponse from(SurveyQuestion q) {
            return new QuestionResponse(
                    q.getId(), q.getOrderIndex(), q.getPrompt(),
                    q.getQuestionType(), q.getMetadata(), q.isRequired());
        }
    }

    public record TemplateResponse(
            UUID id,
            String code,
            String name,
            String description,
            boolean anonymous,
            boolean active,
            int questionCount,
            List<QuestionResponse> questions,
            OffsetDateTime createdAt) {

        public static TemplateResponse from(SurveyTemplate t, List<QuestionResponse> questions) {
            return new TemplateResponse(
                    t.getId(), t.getCode(), t.getName(), t.getDescription(),
                    t.isAnonymous(), t.isActive(),
                    questions.size(), questions, t.getCreatedAt());
        }
    }

    // ── Campaigns ───────────────────────────────────────────────────────

    public record CampaignRequest(
            @NotNull UUID templateId,
            @NotBlank String name,
            String description,
            @NotNull LocalDate opensOn,
            @NotNull LocalDate closesOn,
            Boolean targetAll) {
    }

    public record CampaignResponse(
            UUID id,
            UUID templateId,
            String templateCode,
            String templateName,
            boolean anonymous,
            String name,
            String description,
            CampaignStatus status,
            LocalDate opensOn,
            LocalDate closesOn,
            boolean targetAll,
            long responseCount,
            OffsetDateTime createdAt) {

        public static CampaignResponse from(SurveyCampaign c, SurveyTemplate t, long responseCount) {
            return new CampaignResponse(
                    c.getId(), c.getTemplateId(),
                    t == null ? null : t.getCode(),
                    t == null ? null : t.getName(),
                    t != null && t.isAnonymous(),
                    c.getName(), c.getDescription(), c.getStatus(),
                    c.getOpensOn(), c.getClosesOn(), c.isTargetAll(),
                    responseCount, c.getCreatedAt());
        }
    }

    // ── Submission ──────────────────────────────────────────────────────

    public record AnswerRequest(
            @NotNull UUID questionId,
            /** RATING_1_5 (1..5), RATING_1_10 (0..10), BOOLEAN (0/1). */
            @Min(0) @Max(10) Integer ratingValue,
            String textValue,
            String choiceValue) {
    }

    public record SubmitRequest(
            @NotNull UUID campaignId,
            String comment,
            @NotNull @Valid List<AnswerRequest> answers) {
    }

    public record AnswerResponse(
            UUID id,
            UUID questionId,
            Integer ratingValue,
            String textValue,
            String choiceValue) {

        public static AnswerResponse from(SurveyAnswer a) {
            return new AnswerResponse(a.getId(), a.getQuestionId(),
                    a.getRatingValue(), a.getTextValue(), a.getChoiceValue());
        }
    }

    public record ResponseResponse(
            UUID id,
            UUID campaignId,
            UUID employeeId,
            OffsetDateTime submittedAt,
            String comment,
            List<AnswerResponse> answers) {

        public static ResponseResponse from(SurveyResponse r, List<AnswerResponse> answers) {
            return new ResponseResponse(
                    r.getId(), r.getCampaignId(), r.getEmployeeId(),
                    r.getSubmittedAt(), r.getComment(), answers);
        }
    }

    // ── Aggregate / results ────────────────────────────────────────────

    /** Per-question summary used by the results dashboard. */
    public record QuestionAggregate(
            UUID questionId,
            String prompt,
            QuestionType questionType,
            int answeredCount,
            /** RATING_*-only: average value, rounded to one decimal. */
            Double averageRating,
            /** RATING_1_10 only: net promoter score (-100 .. +100). */
            Integer netPromoterScore,
            /** RATING_*-only: counts per integer score. */
            Map<Integer, Integer> distribution,
            /** MULTIPLE_CHOICE-only: counts per choice string. */
            Map<String, Integer> choiceTallies,
            /** TEXT-only: sample of (up to N) free-text comments. */
            List<String> textSamples) {
    }

    public record CampaignResults(
            UUID campaignId,
            String campaignName,
            int responseCount,
            List<QuestionAggregate> questions) {
    }
}
