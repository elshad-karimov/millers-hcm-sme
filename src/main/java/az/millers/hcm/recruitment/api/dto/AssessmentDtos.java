package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.recruitment.domain.Assessment;

/** M287 — Recruitment PRD §22 assessment DTOs. */
public final class AssessmentDtos {

    private AssessmentDtos() {}

    public record AssessmentRequest(
            @NotNull Assessment.Type assessmentType,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 160) String provider,
            BigDecimal maxScore,
            BigDecimal passingScore,
            LocalDate validUntil,
            Boolean blocksHire) {}

    /** Status/score update (start / complete / score / pass / fail). */
    public record AssessmentUpdate(
            @NotNull Assessment.Status status,
            BigDecimal score,
            Assessment.Result result,
            String notes,
            UUID attachmentId) {}

    public record AssessmentResponse(
            UUID id,
            String assessmentNo,
            UUID applicationId,
            Assessment.Type assessmentType,
            String name,
            String provider,
            Assessment.Status status,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal passingScore,
            Assessment.Result result,
            String notes,
            UUID attachmentId,
            boolean blocksHire,
            OffsetDateTime assignedAt,
            OffsetDateTime completedAt,
            LocalDate validUntil,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static AssessmentResponse from(Assessment a) {
            return new AssessmentResponse(
                    a.getId(), a.getAssessmentNo(), a.getApplicationId(),
                    a.getAssessmentType(), a.getName(), a.getProvider(),
                    a.getStatus(), a.getScore(), a.getMaxScore(), a.getPassingScore(),
                    a.getResult(), a.getNotes(), a.getAttachmentId(),
                    a.isBlocksHire(), a.getAssignedAt(), a.getCompletedAt(),
                    a.getValidUntil(), a.getCreatedAt(), a.getUpdatedAt());
        }
    }
}
