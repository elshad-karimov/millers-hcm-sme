package az.millers.hcm.recruitment.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.recruitment.domain.JobPosting;

/** M278 — Recruitment PRD §8 job posting DTOs. */
public final class JobPostingDtos {

    private JobPostingDtos() {}

    public record PostingRequest(
            @NotNull JobPosting.Channel channel,
            /** ISO 639-1; defaults to az server-side. */
            @Size(max = 5) String language,
            /** Blank fields default from the vacancy at create time. */
            @Size(max = 200) String title,
            String description,
            String requirements,
            String benefitsDescription,
            Boolean salaryVisible,
            LocalDate applicationDeadline) {}

    public record PostingResponse(
            UUID id,
            String postingNo,
            UUID vacancyId,
            JobPosting.Channel channel,
            String language,
            String title,
            String description,
            String requirements,
            String benefitsDescription,
            boolean salaryVisible,
            LocalDate applicationDeadline,
            JobPosting.Status status,
            OffsetDateTime publishedAt,
            String publishedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static PostingResponse from(JobPosting p) {
            return new PostingResponse(
                    p.getId(), p.getPostingNo(), p.getVacancyId(),
                    p.getChannel(), p.getLanguage(), p.getTitle(),
                    p.getDescription(), p.getRequirements(),
                    p.getBenefitsDescription(), p.isSalaryVisible(),
                    p.getApplicationDeadline(), p.getStatus(),
                    p.getPublishedAt(), p.getPublishedBy(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
