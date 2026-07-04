package az.millers.hcm.letters.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.letters.domain.LetterOutputFormat;
import az.millers.hcm.letters.domain.LetterTemplate;

public record LetterTemplateResponse(
        UUID id, String code, String name, String description,
        String body, Object placeholdersJson,
        LetterOutputFormat outputFormat,
        boolean requiresApproval, boolean active,
        /** M139 — ISO 639-1 alpha-2 lowercase. */
        String language,
        OffsetDateTime createdAt, String createdBy,
        OffsetDateTime updatedAt, String updatedBy) {

    public static LetterTemplateResponse from(LetterTemplate t) {
        return new LetterTemplateResponse(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getBody(), t.getPlaceholdersJson(),
                t.getOutputFormat(),
                t.isRequiresApproval(), t.isActive(),
                t.getLanguage(),
                t.getCreatedAt(), t.getCreatedBy(),
                t.getUpdatedAt(), t.getUpdatedBy());
    }
}
