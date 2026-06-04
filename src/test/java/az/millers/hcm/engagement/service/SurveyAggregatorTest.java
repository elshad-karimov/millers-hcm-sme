package az.millers.hcm.engagement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.engagement.api.dto.SurveyDtos.QuestionAggregate;
import az.millers.hcm.engagement.domain.QuestionType;
import az.millers.hcm.engagement.domain.SurveyAnswer;
import az.millers.hcm.engagement.domain.SurveyQuestion;

/**
 * Pure-math pinning for the M116 survey aggregator.
 *
 * <p>The two arithmetic methods worth pinning are NPS (the standard
 * formula HR teams quote externally) and the rating average (used on the
 * dashboard and in the per-question chart). Mis-implementing NPS — wrong
 * promoter band, wrong divisor, off-by-one rounding — leaks straight into
 * board-pack numbers and customer-facing claims.
 */
class SurveyAggregatorTest {

    // ── nps() ───────────────────────────────────────────────────────────

    @Test
    void npsZeroForEmptyList() {
        assertThat(SurveyAggregator.nps(List.of())).isZero();
    }

    @Test
    void npsZeroForNullList() {
        assertThat(SurveyAggregator.nps(null)).isZero();
    }

    @Test
    void npsAllPromotersIsHundred() {
        assertThat(SurveyAggregator.nps(List.of(9, 9, 10, 10))).isEqualTo(100);
    }

    @Test
    void npsAllDetractorsIsMinusHundred() {
        assertThat(SurveyAggregator.nps(List.of(0, 3, 5, 6))).isEqualTo(-100);
    }

    @Test
    void npsAllPassivesIsZero() {
        assertThat(SurveyAggregator.nps(List.of(7, 7, 8, 8))).isZero();
    }

    @Test
    void npsMixedClassic() {
        // 6 responses: 3 promoters (9,9,10), 2 passives (7,8), 1 detractor (3).
        // (3 - 1) / 6 = 0.333... → 33 (half-up).
        assertThat(SurveyAggregator.nps(List.of(9, 9, 10, 7, 8, 3))).isEqualTo(33);
    }

    @Test
    void npsDropsNullsAndOutOfRange() {
        // Nulls and any score outside 0..10 are silently dropped — a bad
        // payload mustn't corrupt the published score.
        List<Integer> mixed = java.util.Arrays.asList(9, 9, null, 10, -1, 11, 5);
        // After dropping: [9, 9, 10, 5] → 3 promoters, 0 passives, 1 detractor over 4.
        // (3 - 1) / 4 = 0.5 → 50.
        assertThat(SurveyAggregator.nps(mixed)).isEqualTo(50);
    }

    @Test
    void npsBoundaryOfPromoter() {
        // Score 9 is the boundary — must count as a promoter, not a passive.
        // Score 8 must be a passive, not a promoter.
        assertThat(SurveyAggregator.nps(List.of(9))).isEqualTo(100);
        assertThat(SurveyAggregator.nps(List.of(8))).isZero();
    }

    @Test
    void npsBoundaryOfDetractor() {
        // Score 6 is the boundary — must count as a detractor, not a passive.
        // Score 7 must be a passive, not a detractor.
        assertThat(SurveyAggregator.nps(List.of(6))).isEqualTo(-100);
        assertThat(SurveyAggregator.nps(List.of(7))).isZero();
    }

    @Test
    void npsRoundsHalfAwayFromZero() {
        // 1 promoter, 1 detractor, 1 passive → (1-1)/3 = 0 → 0.
        assertThat(SurveyAggregator.nps(List.of(9, 7, 3))).isZero();
        // 2 promoters, 1 detractor over 3 → (2-1)/3 = 0.333... → 33.
        assertThat(SurveyAggregator.nps(List.of(9, 10, 3))).isEqualTo(33);
    }

    // ── averageRating() ─────────────────────────────────────────────────

    @Test
    void averageNullForEmpty() {
        assertThat(SurveyAggregator.averageRating(List.of())).isNull();
        assertThat(SurveyAggregator.averageRating(null)).isNull();
    }

    @Test
    void averageRoundsToOneDecimal() {
        // (1 + 2 + 3 + 4 + 5) / 5 = 3.0
        assertThat(SurveyAggregator.averageRating(List.of(1, 2, 3, 4, 5))).isEqualTo(3.0);
        // (1 + 1 + 2) / 3 = 1.333... → 1.3
        assertThat(SurveyAggregator.averageRating(List.of(1, 1, 2))).isEqualTo(1.3);
    }

    @Test
    void averageDropsNulls() {
        assertThat(SurveyAggregator.averageRating(
                java.util.Arrays.asList(5, null, 5, null, 5))).isEqualTo(5.0);
    }

    // ── aggregate() — full shape per question type ──────────────────────

    @Test
    void aggregateRating10IncludesNps() {
        SurveyQuestion q = question(QuestionType.RATING_1_10, "How likely?");
        List<SurveyAnswer> answers = List.of(
                answer(q.getId(), 10, null, null),
                answer(q.getId(), 9, null, null),
                answer(q.getId(), 6, null, null));
        QuestionAggregate agg = SurveyAggregator.aggregate(q, answers, 10);
        assertThat(agg.questionType()).isEqualTo(QuestionType.RATING_1_10);
        assertThat(agg.answeredCount()).isEqualTo(3);
        assertThat(agg.netPromoterScore()).isNotNull();
        // 2 promoters - 1 detractor over 3 = 33.
        assertThat(agg.netPromoterScore()).isEqualTo(33);
        assertThat(agg.distribution()).containsEntry(10, 1)
                .containsEntry(9, 1)
                .containsEntry(6, 1);
    }

    @Test
    void aggregateRating5SkipsNps() {
        SurveyQuestion q = question(QuestionType.RATING_1_5, "Satisfaction?");
        List<SurveyAnswer> answers = List.of(
                answer(q.getId(), 5, null, null),
                answer(q.getId(), 4, null, null),
                answer(q.getId(), 3, null, null));
        QuestionAggregate agg = SurveyAggregator.aggregate(q, answers, 10);
        assertThat(agg.netPromoterScore()).isNull();   // NPS is a 0-10 concept
        assertThat(agg.averageRating()).isEqualTo(4.0);
    }

    @Test
    void aggregateBooleanTalliesYesNo() {
        SurveyQuestion q = question(QuestionType.BOOLEAN, "Recommend?");
        List<SurveyAnswer> answers = List.of(
                answer(q.getId(), 1, null, null),
                answer(q.getId(), 1, null, null),
                answer(q.getId(), 0, null, null));
        QuestionAggregate agg = SurveyAggregator.aggregate(q, answers, 10);
        assertThat(agg.answeredCount()).isEqualTo(3);
        assertThat(agg.distribution()).containsEntry(0, 1).containsEntry(1, 2);
        // 2/3 → 0.666... → 0.7
        assertThat(agg.averageRating()).isEqualTo(0.7);
    }

    @Test
    void aggregateMultipleChoiceTalliesPerOption() {
        SurveyQuestion q = question(QuestionType.MULTIPLE_CHOICE, "Best perk?");
        List<SurveyAnswer> answers = List.of(
                answer(q.getId(), null, null, "WFH"),
                answer(q.getId(), null, null, "WFH"),
                answer(q.getId(), null, null, "Snacks"));
        QuestionAggregate agg = SurveyAggregator.aggregate(q, answers, 10);
        assertThat(agg.answeredCount()).isEqualTo(3);
        assertThat(agg.choiceTallies()).containsEntry("WFH", 2)
                .containsEntry("Snacks", 1);
        assertThat(agg.distribution()).isNull();    // not a rating type
        assertThat(agg.netPromoterScore()).isNull();
    }

    @Test
    void aggregateTextCollectsSamplesCappedByLimit() {
        SurveyQuestion q = question(QuestionType.TEXT, "Anything else?");
        List<SurveyAnswer> answers = List.of(
                answer(q.getId(), null, "great team", null),
                answer(q.getId(), null, "love the office", null),
                answer(q.getId(), null, "more snacks please", null),
                // empty + null are filtered.
                answer(q.getId(), null, "", null),
                answer(q.getId(), null, null, null));
        QuestionAggregate agg = SurveyAggregator.aggregate(q, answers, 2);
        // The two non-empty samples kept, in order; empty/null dropped.
        assertThat(agg.answeredCount()).isEqualTo(3);
        assertThat(agg.textSamples()).hasSize(2);
        assertThat(agg.textSamples()).containsExactly("great team", "love the office");
    }

    @Test
    void aggregateEmptyAnswerListSafe() {
        SurveyQuestion q = question(QuestionType.RATING_1_10, "Nobody answered yet");
        QuestionAggregate agg = SurveyAggregator.aggregate(q, List.of(), 10);
        assertThat(agg.answeredCount()).isZero();
        assertThat(agg.averageRating()).isNull();
        assertThat(agg.netPromoterScore()).isZero();   // empty → 0, not null
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static SurveyQuestion question(QuestionType type, String prompt) {
        SurveyQuestion q = new SurveyQuestion();
        q.setId(UUID.randomUUID());
        q.setTemplateId(UUID.randomUUID());
        q.setOrderIndex(0);
        q.setPrompt(prompt);
        q.setQuestionType(type);
        q.setRequired(true);
        return q;
    }

    private static SurveyAnswer answer(UUID questionId, Integer rating, String text, String choice) {
        SurveyAnswer a = new SurveyAnswer();
        a.setId(UUID.randomUUID());
        a.setResponseId(UUID.randomUUID());
        a.setQuestionId(questionId);
        a.setRatingValue(rating);
        a.setTextValue(text);
        a.setChoiceValue(choice);
        return a;
    }
}
