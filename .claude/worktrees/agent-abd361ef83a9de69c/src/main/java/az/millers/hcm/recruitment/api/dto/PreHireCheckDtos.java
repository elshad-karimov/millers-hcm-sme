package az.millers.hcm.recruitment.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.recruitment.domain.PreHireCheck;

/** M286 — Recruitment PRD §25-§27 pre-hire check DTOs. */
public final class PreHireCheckDtos {

    private PreHireCheckDtos() {}

    public record CheckRequest(
            @NotNull PreHireCheck.Type checkType,
            @Size(max = 160) String provider,
            @Size(max = 160) String subjectName,
            @Size(max = 160) String subjectContact,
            Boolean blocksHire) {}

    /** Status/result update (request / complete / pass / fail / review). */
    public record CheckUpdate(
            @NotNull PreHireCheck.Status status,
            PreHireCheck.Result result,
            String resultNotes,
            UUID attachmentId) {}

    public record CheckResponse(
            UUID id,
            String checkNo,
            UUID applicationId,
            PreHireCheck.Type checkType,
            PreHireCheck.Status status,
            String provider,
            String subjectName,
            String subjectContact,
            PreHireCheck.Result result,
            /** Null when redacted (confidential medical detail, PRD §27). */
            String resultNotes,
            /** True when resultNotes was hidden from this caller. */
            boolean resultRedacted,
            UUID attachmentId,
            boolean blocksHire,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static CheckResponse from(PreHireCheck c, boolean redact) {
            return new CheckResponse(
                    c.getId(), c.getCheckNo(), c.getApplicationId(),
                    c.getCheckType(), c.getStatus(), c.getProvider(),
                    c.getSubjectName(), c.getSubjectContact(),
                    c.getResult(),
                    redact ? null : c.getResultNotes(),
                    redact && c.getResultNotes() != null,
                    c.getAttachmentId(), c.isBlocksHire(),
                    c.getRequestedAt(), c.getCompletedAt(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
