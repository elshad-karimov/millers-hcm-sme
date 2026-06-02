package az.millers.hcm.letters.api.dto;

import az.millers.hcm.letters.domain.LetterOutputFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LetterTemplateRequest(
        @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "code must be uppercase alphanumeric")
        String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @NotBlank String body,
        Object placeholdersJson,
        LetterOutputFormat outputFormat,
        Boolean requiresApproval,
        Boolean active) {
}
