package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidateNote;
import az.millers.hcm.recruitment.domain.CandidateNoteKind;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;
import az.millers.hcm.recruitment.domain.CandidateTag;
import az.millers.hcm.recruitment.domain.CandidateSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** M87 talent-pool DTOs — kept together; all flat record shapes. */
public final class TalentPoolDtos {
    private TalentPoolDtos() {}

    /** Compact candidate projection — list view. */
    public record PoolCandidateRow(
            UUID id, String candidateNo,
            String firstName, String lastName,
            String email, String phone,
            CandidateSource source,
            BigDecimal experienceYears,
            BigDecimal expectedSalary, String currency,
            CandidatePoolStatus poolStatus,
            OffsetDateTime lastContactedAt,
            OffsetDateTime createdAt,
            List<String> tags) {

        public static PoolCandidateRow from(Candidate c, List<String> tags) {
            return new PoolCandidateRow(
                    c.getId(), c.getCandidateNo(),
                    c.getFirstName(), c.getLastName(),
                    c.getEmail(), c.getPhone(),
                    c.getSource(), c.getExperienceYears(),
                    c.getExpectedSalary(), c.getCurrency(),
                    c.getPoolStatus(), c.getLastContactedAt(),
                    c.getCreatedAt(), tags);
        }
    }

    public record PoolSearchResponse(
            int page, int size, long totalElements, int totalPages,
            List<PoolCandidateRow> content) {}

    public record TagRequest(
            @NotBlank @Size(max = 60) String tag) {}

    public record TagResponse(
            UUID id, UUID candidateId, String tag,
            OffsetDateTime createdAt, String createdBy) {
        public static TagResponse from(CandidateTag t) {
            return new TagResponse(t.getId(), t.getCandidateId(), t.getTag(),
                    t.getCreatedAt(), t.getCreatedBy());
        }
    }

    public record NoteRequest(
            @NotNull CandidateNoteKind kind,
            @NotBlank String body,
            LocalDate contactDate) {}

    public record NoteResponse(
            UUID id, UUID candidateId, CandidateNoteKind kind,
            String body, LocalDate contactDate,
            OffsetDateTime createdAt, String createdBy) {
        public static NoteResponse from(CandidateNote n) {
            return new NoteResponse(n.getId(), n.getCandidateId(), n.getKind(),
                    n.getBody(), n.getContactDate(), n.getCreatedAt(), n.getCreatedBy());
        }
    }

    public record PoolStatusChange(
            @NotNull CandidatePoolStatus newStatus,
            @Size(max = 4000) String reason) {}
}
