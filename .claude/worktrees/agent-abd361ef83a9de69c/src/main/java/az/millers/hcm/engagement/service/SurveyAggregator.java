package az.millers.hcm.engagement.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import az.millers.hcm.engagement.api.dto.SurveyDtos.QuestionAggregate;
import az.millers.hcm.engagement.domain.QuestionType;
import az.millers.hcm.engagement.domain.SurveyAnswer;
import az.millers.hcm.engagement.domain.SurveyQuestion;

/**
 * Pure-math aggregation of survey answers (M116).
 *
 * <p>Split out of {@link SurveyService} so the math (averages, NPS,
 * distributions) can be pinned by unit tests without spinning up Spring.
 * The two methods worth knowing:
 *
 * <ul>
 *   <li>{@link #nps(List)} — classic Net Promoter Score on a 0-10 scale.
 *       Promoters (9-10) minus detractors (0-6), as a percentage of total
 *       responses. Returns a value in {@code [-100, +100]}.</li>
 *   <li>{@link #aggregate(SurveyQuestion, List, int)} — full per-question
 *       summary (count, average, NPS where applicable, integer
 *       distribution, choice tallies, text samples).</li>
 * </ul>
 */
public final class SurveyAggregator {

    /** Max text-answer samples surfaced per TEXT question. */
    static final int TEXT_SAMPLE_LIMIT = 25;

    private SurveyAggregator() {}

    /**
     * Net Promoter Score over a list of 0-10 ratings.
     *
     * <p>Promoters are scores 9 or 10; detractors are 0..6; passives (7..8)
     * are counted in the denominator but don't move the score. Values
     * outside the 0..10 range are dropped (defensive — a bad payload
     * shouldn't crash the dashboard).
     *
     * <p>Returns {@code 0} for an empty list.
     */
    public static int nps(List<Integer> scores) {
        if (scores == null || scores.isEmpty()) return 0;
        int total = 0;
        int promoters = 0;
        int detractors = 0;
        for (Integer s : scores) {
            if (s == null) continue;
            int v = s;
            if (v < 0 || v > 10) continue;
            total++;
            if (v >= 9) promoters++;
            else if (v <= 6) detractors++;
        }
        if (total == 0) return 0;
        // (promoters - detractors) / total × 100 — integer math, half-up.
        double pct = ((double) (promoters - detractors) / total) * 100.0;
        return (int) Math.round(pct);
    }

    /**
     * Arithmetic mean of a list of integer scores, rounded to one decimal.
     * Returns {@code null} for empty input so the UI can render "—".
     */
    public static Double averageRating(List<Integer> scores) {
        if (scores == null || scores.isEmpty()) return null;
        long sum = 0;
        int count = 0;
        for (Integer s : scores) {
            if (s == null) continue;
            sum += s;
            count++;
        }
        if (count == 0) return null;
        double avg = (double) sum / count;
        return Math.round(avg * 10.0) / 10.0;
    }

    /**
     * Full per-question summary. The shape of the result depends on the
     * question type — {@code averageRating} and {@code distribution} are
     * populated for the rating types and BOOLEAN; {@code choiceTallies}
     * for MULTIPLE_CHOICE; {@code textSamples} for TEXT.
     */
    public static QuestionAggregate aggregate(SurveyQuestion question,
                                                List<SurveyAnswer> answersForQuestion,
                                                int textSampleLimit) {
        if (question == null) {
            throw new IllegalArgumentException("question is required");
        }
        QuestionType type = question.getQuestionType();
        int answered = 0;
        Double avg = null;
        Integer nps = null;
        Map<Integer, Integer> distribution = null;
        Map<String, Integer> choices = null;
        List<String> samples = null;

        switch (type) {
            case RATING_1_5, RATING_1_10, BOOLEAN -> {
                List<Integer> values = answersForQuestion.stream()
                        .map(SurveyAnswer::getRatingValue)
                        .filter(java.util.Objects::nonNull)
                        .toList();
                answered = values.size();
                avg = averageRating(values);
                distribution = bucket(values);
                if (type == QuestionType.RATING_1_10) {
                    nps = nps(values);
                }
            }
            case MULTIPLE_CHOICE -> {
                List<String> values = answersForQuestion.stream()
                        .map(SurveyAnswer::getChoiceValue)
                        .filter(java.util.Objects::nonNull)
                        .toList();
                answered = values.size();
                choices = values.stream().collect(Collectors.toMap(
                        s -> s, s -> 1, Integer::sum, java.util.LinkedHashMap::new));
            }
            case TEXT -> {
                List<String> values = answersForQuestion.stream()
                        .map(SurveyAnswer::getTextValue)
                        .filter(java.util.Objects::nonNull)
                        .filter(s -> !s.isBlank())
                        .toList();
                answered = values.size();
                samples = values.stream()
                        .limit(Math.max(0, textSampleLimit))
                        .toList();
            }
        }

        return new QuestionAggregate(
                question.getId(),
                question.getPrompt(),
                type,
                answered,
                avg,
                nps,
                distribution,
                choices,
                samples);
    }

    /** Package-private — pinned by the unit test. */
    static Map<Integer, Integer> bucket(List<Integer> values) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Integer v : values) {
            if (v == null) continue;
            map.merge(v, 1, Integer::sum);
        }
        return map;
    }

    @SuppressWarnings("unused")
    private static List<UUID> emptyIds() { return new ArrayList<>(); }
}
