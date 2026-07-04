package az.millers.hcm.learning.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.learning.domain.AttemptStatus;
import az.millers.hcm.learning.domain.QuizAnswer;
import az.millers.hcm.learning.domain.QuizAttempt;

public record AttemptResponse(
        UUID id,
        UUID enrollmentId,
        int attemptNo,
        OffsetDateTime startedAt,
        OffsetDateTime submittedAt,
        AttemptStatus status,
        Integer totalPoints,
        /** Earned points; BigDecimal to surface MULTI_SELECT partial credit (e.g. 1.33). */
        BigDecimal earnedPoints,
        BigDecimal scorePercent,
        Integer passingScore,
        List<AnswerView> answers) {

    public record AnswerView(
            UUID questionId,
            List<String> selectedKeys,
            boolean correct,
            /** Awarded points; may be fractional for MULTI_SELECT partial credit. */
            BigDecimal pointsAwarded) {

        public static AnswerView from(QuizAnswer a) {
            return new AnswerView(a.getQuestionId(), a.getSelectedKeys(), a.isCorrect(), a.getPointsAwarded());
        }
    }

    public static AttemptResponse from(QuizAttempt a, List<QuizAnswer> answers) {
        return new AttemptResponse(
                a.getId(), a.getEnrollmentId(), a.getAttemptNo(),
                a.getStartedAt(), a.getSubmittedAt(), a.getStatus(),
                a.getTotalPoints(), a.getEarnedPoints(), a.getScorePercent(),
                a.getPassingScore(),
                answers == null ? List.of() : answers.stream().map(AnswerView::from).toList());
    }
}
