package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import az.millers.hcm.recruitment.domain.ApplicationAnswer;
import az.millers.hcm.recruitment.domain.ScreeningQuestion;
import az.millers.hcm.recruitment.domain.ScreeningQuestionType;

/** M289 — Recruitment PRD §15 knockout / screening question DTOs. */
public final class ScreeningDtos {

    private ScreeningDtos() {}

    /** HR create / edit of one screening question. */
    public record QuestionRequest(
            int ordinal,
            @NotBlank String questionText,
            @NotNull ScreeningQuestionType questionType,
            String options,
            boolean knockout,
            boolean required,
            Boolean expectedBoolean,
            String acceptableOptions,
            BigDecimal minValue,
            BigDecimal maxValue,
            Boolean active) {}

    /** HR view — includes the pass/fail rule. */
    public record QuestionResponse(
            UUID id,
            UUID postingId,
            int ordinal,
            String questionText,
            ScreeningQuestionType questionType,
            String options,
            boolean knockout,
            boolean required,
            Boolean expectedBoolean,
            String acceptableOptions,
            BigDecimal minValue,
            BigDecimal maxValue,
            boolean active) {

        public static QuestionResponse from(ScreeningQuestion q) {
            return new QuestionResponse(q.getId(), q.getPostingId(), q.getOrdinal(),
                    q.getQuestionText(), q.getQuestionType(), q.getOptions(),
                    q.isKnockout(), q.isRequired(), q.getExpectedBoolean(),
                    q.getAcceptableOptions(), q.getMinValue(), q.getMaxValue(),
                    q.isActive());
        }
    }

    /**
     * Candidate-facing view — deliberately omits the rule fields
     * (expectedBoolean / acceptableOptions / min/max / knockout) so the
     * public career site never leaks what answer passes.
     */
    public record PublicQuestion(
            UUID id,
            int ordinal,
            String questionText,
            ScreeningQuestionType questionType,
            String options,
            boolean required) {

        public static PublicQuestion from(ScreeningQuestion q) {
            return new PublicQuestion(q.getId(), q.getOrdinal(), q.getQuestionText(),
                    q.getQuestionType(), q.getOptions(), q.isRequired());
        }
    }

    /** One candidate answer submitted at apply time. */
    public record ScreeningAnswer(
            @NotNull UUID questionId,
            String answerText) {}

    /** Recruiter view of a stored answer + its computed outcome. */
    public record AnswerResponse(
            UUID questionId,
            String questionText,
            String answerText,
            boolean passed,
            boolean knockout) {

        public static AnswerResponse from(ApplicationAnswer a) {
            return new AnswerResponse(a.getQuestionId(), a.getQuestionTextSnapshot(),
                    a.getAnswerText(), a.isPassed(), a.isKnockout());
        }
    }

    /** Outcome of evaluating a candidate's answers against a posting. */
    public record ScreeningResult(
            boolean knockedOut,
            int answered,
            List<String> failedKnockouts) {}
}
