package az.millers.hcm.learning.api.dto;

import java.util.List;
import java.util.Map;

import az.millers.hcm.learning.domain.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record QuestionRequest(
        Integer questionNo,
        @NotNull QuestionType questionType,
        @NotBlank String questionText,
        @NotEmpty List<Map<String, Object>> choices,
        @NotEmpty List<String> correctKeys,
        String explanation,
        Integer points) {
}
