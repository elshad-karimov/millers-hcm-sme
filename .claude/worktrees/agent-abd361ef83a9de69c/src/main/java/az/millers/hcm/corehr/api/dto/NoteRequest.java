package az.millers.hcm.corehr.api.dto;

import az.millers.hcm.corehr.domain.NoteType;
import az.millers.hcm.corehr.domain.NoteVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteRequest(
        NoteType noteType,
        @NotBlank @Size(max = 8000) String noteBody,
        NoteVisibility visibilityLevel,
        Boolean pinned) {
}
