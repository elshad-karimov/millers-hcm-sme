package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ProbationOutcome;
import az.millers.hcm.lifecycle.domain.ProbationReview;
import az.millers.hcm.lifecycle.domain.ProbationReviewStatus;
import az.millers.hcm.lifecycle.domain.ProbationReviewType;

public record ProbationReviewResponse(
        UUID id,
        UUID employeeId,
        UUID contractId,
        ProbationReviewType reviewType,
        LocalDate scheduledDate,
        LocalDate completedDate,
        UUID managerEmployeeId,
        UUID reviewerEmployeeId,
        String managerFeedback,
        Integer managerRating,
        String hrFeedback,
        Integer hrRating,
        ProbationReviewStatus status,
        ProbationOutcome outcome,
        LocalDate effectiveDate,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static ProbationReviewResponse from(ProbationReview r) {
        return new ProbationReviewResponse(
                r.getId(),
                r.getEmployeeId(),
                r.getContractId(),
                r.getReviewType(),
                r.getScheduledDate(),
                r.getCompletedDate(),
                r.getManagerEmployeeId(),
                r.getReviewerEmployeeId(),
                r.getManagerFeedback(),
                r.getManagerRating(),
                r.getHrFeedback(),
                r.getHrRating(),
                r.getStatus(),
                r.getOutcome(),
                r.getEffectiveDate(),
                r.getNotes(),
                r.getCreatedAt(),
                r.getCreatedBy(),
                r.getUpdatedAt(),
                r.getUpdatedBy());
    }
}
