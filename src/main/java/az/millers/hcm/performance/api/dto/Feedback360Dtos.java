package az.millers.hcm.performance.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.performance.domain.FeedbackNomination;
import az.millers.hcm.performance.domain.FeedbackQuestion;
import az.millers.hcm.performance.domain.FeedbackQuestionnaire;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs for 360° nominations + questionnaires (HCM_12 M395). */
public final class Feedback360Dtos {

    private Feedback360Dtos() {}

    // ── Questionnaires (§13.3) ───────────────────────────────────────────────

    public record QuestionRequest(
            @NotBlank @Size(max = 500) String questionText,
            String questionType,
            String category,
            Boolean required) {
    }

    public record QuestionnaireRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 200) String name,
            String description,
            Boolean active,
            @NotEmpty List<@Valid QuestionRequest> questions) {
    }

    public record QuestionResponse(
            UUID id, int questionOrder, String questionText, String questionType,
            String category, boolean required) {

        public static QuestionResponse from(FeedbackQuestion q) {
            return new QuestionResponse(q.getId(), q.getQuestionOrder(), q.getQuestionText(),
                    q.getQuestionType(), q.getCategory(), q.isRequired());
        }
    }

    public record QuestionnaireResponse(
            UUID id, String code, String name, String description, boolean active,
            List<QuestionResponse> questions, OffsetDateTime createdAt) {

        public static QuestionnaireResponse from(FeedbackQuestionnaire q, List<FeedbackQuestion> questions) {
            return new QuestionnaireResponse(q.getId(), q.getCode(), q.getName(), q.getDescription(),
                    q.isActive(),
                    questions == null ? List.of() : questions.stream().map(QuestionResponse::from).toList(),
                    q.getCreatedAt());
        }
    }

    // ── Nominations (§13.2) ──────────────────────────────────────────────────

    public record NominateRequest(
            @NotNull UUID cycleId,
            @NotNull UUID subjectEmployeeId,
            @NotNull UUID reviewerEmployeeId,
            @NotBlank String relationship,
            UUID questionnaireId,
            Boolean anonymous,
            LocalDate dueDate) {
    }

    public record NominationResponse(
            UUID id, UUID cycleId, UUID subjectEmployeeId, String subjectName,
            UUID reviewerEmployeeId, String reviewerName, String relationship,
            UUID questionnaireId, String questionnaireName, boolean anonymous, String status,
            String nominatedBy, LocalDate dueDate, String declineReason, UUID feedbackId,
            OffsetDateTime createdAt) {

        public static NominationResponse from(FeedbackNomination n, String subjectName,
                                              String reviewerName, String questionnaireName) {
            return new NominationResponse(n.getId(), n.getCycleId(), n.getSubjectEmployeeId(),
                    subjectName, n.getReviewerEmployeeId(), reviewerName, n.getRelationship(),
                    n.getQuestionnaireId(), questionnaireName, n.isAnonymous(), n.getStatus(),
                    n.getNominatedBy(), n.getDueDate(), n.getDeclineReason(), n.getFeedbackId(),
                    n.getCreatedAt());
        }
    }

    /** §13.4 completeness summary for a subject in a cycle. */
    public record NominationSummary(
            int nominated, int approved, int completed, int declined,
            Integer minReviewers, Integer maxReviewers, boolean meetsMinimum) {
    }
}
