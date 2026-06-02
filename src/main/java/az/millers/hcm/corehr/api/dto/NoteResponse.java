package az.millers.hcm.corehr.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeNote;
import az.millers.hcm.corehr.domain.NoteType;
import az.millers.hcm.corehr.domain.NoteVisibility;

public record NoteResponse(
        UUID id,
        UUID employeeId,
        NoteType noteType,
        String noteBody,
        NoteVisibility visibilityLevel,
        boolean pinned,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static NoteResponse from(EmployeeNote n) {
        return new NoteResponse(
                n.getId(),
                n.getEmployeeId(),
                n.getNoteType(),
                n.getNoteBody(),
                n.getVisibilityLevel(),
                n.isPinned(),
                n.getCreatedAt(),
                n.getCreatedBy(),
                n.getUpdatedAt(),
                n.getUpdatedBy());
    }
}
