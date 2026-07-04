package az.millers.hcm.engagement.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.engagement.api.dto.SurveyDtos.AnswerRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.QuestionRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.TemplateRequest;
import az.millers.hcm.engagement.domain.CampaignStatus;
import az.millers.hcm.engagement.domain.QuestionType;
import az.millers.hcm.engagement.domain.SurveyQuestion;

/**
 * Pins the M116 validation + state-machine math.
 *
 * <p>The three things worth pinning here:
 * <ul>
 *   <li>validateTemplate — duplicate orderIndex / empty question set /
 *       missing prompt are caught before we hit the DB.</li>
 *   <li>validateTransition — enforces DRAFT → ACTIVE → CLOSED. Skipping
 *       straight from DRAFT to CLOSED, or reopening a CLOSED campaign,
 *       would corrupt the response-window invariant.</li>
 *   <li>validateAnswer — type-correct payloads only (RATING_1_5 wants
 *       1..5, RATING_1_10 wants 0..10, BOOLEAN wants 0/1, etc.).</li>
 * </ul>
 */
class SurveyServiceTest {

    // ── validateTemplate() ──────────────────────────────────────────────

    @Test
    void validateTemplateRejectsEmptyQuestionList() {
        TemplateRequest req = new TemplateRequest(
                "T", "Tpl", null, false, true, List.of());
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateTemplate(req))
                .withMessageContaining("at least one question");
    }

    @Test
    void validateTemplateAcceptsCanonical() {
        TemplateRequest req = new TemplateRequest(
                "T", "Tpl", null, false, true,
                List.of(
                        new QuestionRequest(0, "Q1", QuestionType.RATING_1_5, null, true),
                        new QuestionRequest(1, "Q2", QuestionType.TEXT, null, false)));
        assertThatNoException().isThrownBy(() -> SurveyService.validateTemplate(req));
    }

    @Test
    void validateTemplateRejectsDuplicateOrderIndex() {
        TemplateRequest req = new TemplateRequest(
                "T", "Tpl", null, false, true,
                List.of(
                        new QuestionRequest(0, "Q1", QuestionType.RATING_1_5, null, true),
                        new QuestionRequest(0, "Q2", QuestionType.TEXT, null, true)));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateTemplate(req))
                .withMessageContaining("Duplicate orderIndex");
    }

    @Test
    void validateTemplateRejectsNegativeOrderIndex() {
        TemplateRequest req = new TemplateRequest(
                "T", "Tpl", null, false, true,
                List.of(new QuestionRequest(-1, "Q1", QuestionType.RATING_1_5, null, true)));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateTemplate(req));
    }

    @Test
    void validateTemplateRejectsBlankPrompt() {
        TemplateRequest req = new TemplateRequest(
                "T", "Tpl", null, false, true,
                List.of(new QuestionRequest(0, "  ", QuestionType.RATING_1_5, null, true)));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateTemplate(req))
                .withMessageContaining("prompt");
    }

    @Test
    void validateTemplateRejectsNullType() {
        TemplateRequest req = new TemplateRequest(
                "T", "Tpl", null, false, true,
                List.of(new QuestionRequest(0, "Q1", null, null, true)));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateTemplate(req));
    }

    // ── validateTransition() ────────────────────────────────────────────

    @Test
    void transitionDraftToActiveAllowed() {
        assertThatNoException().isThrownBy(
                () -> SurveyService.validateTransition(CampaignStatus.DRAFT, CampaignStatus.ACTIVE));
    }

    @Test
    void transitionDraftToCancelledAllowed() {
        assertThatNoException().isThrownBy(
                () -> SurveyService.validateTransition(CampaignStatus.DRAFT, CampaignStatus.CANCELLED));
    }

    @Test
    void transitionActiveToClosedAllowed() {
        assertThatNoException().isThrownBy(
                () -> SurveyService.validateTransition(CampaignStatus.ACTIVE, CampaignStatus.CLOSED));
    }

    @Test
    void transitionActiveToCancelledAllowed() {
        assertThatNoException().isThrownBy(
                () -> SurveyService.validateTransition(CampaignStatus.ACTIVE, CampaignStatus.CANCELLED));
    }

    @Test
    void transitionDraftToClosedRejected() {
        // Can't skip ACTIVE — every closed campaign must have had an open window.
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateTransition(
                        CampaignStatus.DRAFT, CampaignStatus.CLOSED));
    }

    @Test
    void transitionClosedReopenRejected() {
        // Reopening a closed campaign would let new responses change a
        // result that's already been reported.
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateTransition(
                        CampaignStatus.CLOSED, CampaignStatus.ACTIVE));
    }

    @Test
    void transitionCancelledReopenRejected() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateTransition(
                        CampaignStatus.CANCELLED, CampaignStatus.ACTIVE));
    }

    // ── validateAnswer() ────────────────────────────────────────────────

    @Test
    void rating15RejectsOutOfBand() {
        SurveyQuestion q = q(QuestionType.RATING_1_5);
        // 0 and 6 are outside [1, 5].
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(0)));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(6)));
    }

    @Test
    void rating15AcceptsBoundaries() {
        SurveyQuestion q = q(QuestionType.RATING_1_5);
        assertThatNoException().isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(1)));
        assertThatNoException().isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(5)));
    }

    @Test
    void rating110AcceptsZeroAndTen() {
        SurveyQuestion q = q(QuestionType.RATING_1_10);
        assertThatNoException().isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(0)));
        assertThatNoException().isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(10)));
    }

    @Test
    void rating110RejectsEleven() {
        SurveyQuestion q = q(QuestionType.RATING_1_10);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(11)));
    }

    @Test
    void booleanAcceptsZeroOrOne() {
        SurveyQuestion q = q(QuestionType.BOOLEAN);
        assertThatNoException().isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(0)));
        assertThatNoException().isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(1)));
    }

    @Test
    void booleanRejectsOtherValues() {
        SurveyQuestion q = q(QuestionType.BOOLEAN);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(2)));
        // Null is also rejected.
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateAnswer(q, ratingAnswer(null)));
    }

    @Test
    void textRequiredRejectsBlank() {
        SurveyQuestion q = q(QuestionType.TEXT);
        AnswerRequest a = new AnswerRequest(q.getId(), null, "  ", null);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateAnswer(q, a));
    }

    @Test
    void textOptionalAllowsBlank() {
        SurveyQuestion q = q(QuestionType.TEXT);
        q.setRequired(false);
        AnswerRequest a = new AnswerRequest(q.getId(), null, null, null);
        assertThatNoException().isThrownBy(() -> SurveyService.validateAnswer(q, a));
    }

    @Test
    void multipleChoiceRequiresAChoice() {
        SurveyQuestion q = q(QuestionType.MULTIPLE_CHOICE);
        AnswerRequest a = new AnswerRequest(q.getId(), null, null, null);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> SurveyService.validateAnswer(q, a));
    }

    @Test
    void multipleChoiceAcceptsChoiceString() {
        SurveyQuestion q = q(QuestionType.MULTIPLE_CHOICE);
        AnswerRequest a = new AnswerRequest(q.getId(), null, null, "WFH");
        assertThatNoException().isThrownBy(() -> SurveyService.validateAnswer(q, a));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static SurveyQuestion q(QuestionType type) {
        SurveyQuestion q = new SurveyQuestion();
        q.setId(UUID.randomUUID());
        q.setTemplateId(UUID.randomUUID());
        q.setOrderIndex(0);
        q.setPrompt("Question");
        q.setQuestionType(type);
        q.setRequired(true);
        return q;
    }

    private static AnswerRequest ratingAnswer(Integer rating) {
        return new AnswerRequest(UUID.randomUUID(), rating, null, null);
    }
}
