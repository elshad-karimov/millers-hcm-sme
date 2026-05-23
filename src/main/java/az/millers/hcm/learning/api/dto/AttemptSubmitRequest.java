package az.millers.hcm.learning.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AttemptSubmitRequest(
        @NotEmpty List<Answer> answers) {

    public record Answer(
            @NotNull UUID questionId,
            @NotEmpty List<String> selectedKeys) {
    }
}
