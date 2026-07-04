package az.millers.hcm.learning.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.learning.domain.QuestionType;
import az.millers.hcm.learning.domain.QuizQuestion;

public record QuestionResponse(
        UUID id,
        UUID courseId,
        int questionNo,
        QuestionType questionType,
        String questionText,
        List<Map<String, Object>> choices,
        List<String> correctKeys,
        String explanation,
        int points) {

    public static QuestionResponse from(QuizQuestion q, boolean includeAnswerKey) {
        return new QuestionResponse(
                q.getId(), q.getCourseId(), q.getQuestionNo(), q.getQuestionType(),
                q.getQuestionText(), q.getChoices(),
                includeAnswerKey ? q.getCorrectKeys() : null,
                includeAnswerKey ? q.getExplanation() : null,
                q.getPoints());
    }
}
