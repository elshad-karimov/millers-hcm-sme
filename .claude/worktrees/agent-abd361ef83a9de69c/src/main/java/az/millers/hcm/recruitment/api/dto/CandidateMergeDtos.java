package az.millers.hcm.recruitment.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** M293 — Recruitment PRD §12 duplicate-detection + merge DTOs. */
public final class CandidateMergeDtos {

    private CandidateMergeDtos() {}

    /** One candidate inside a duplicate group. */
    public record DuplicateCandidate(
            UUID id,
            String candidateNo,
            String fullName,
            String email,
            String phone,
            String source,
            long applicationCount,
            OffsetDateTime createdAt) {}

    /** A set of candidates that look like the same person. */
    public record DuplicateGroup(
            String matchType,   // EMAIL | PHONE | NAME
            String matchValue,
            List<DuplicateCandidate> candidates) {}

    /** Merge {@code duplicateId} into {@code primaryId} (primary survives). */
    public record MergeRequest(
            @NotNull UUID primaryId,
            @NotNull UUID duplicateId) {}

    /** What the merge moved / left behind. */
    public record MergeResult(
            UUID primaryId,
            UUID mergedId,
            int applicationsMoved,
            int applicationsConflicted,
            int notesMoved,
            int tagsMoved,
            int educationMoved,
            int experienceMoved,
            int skillsMoved,
            int documentsMoved) {}
}
