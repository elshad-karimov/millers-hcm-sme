package az.millers.hcm.learning.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.learning.domain.TrainingPlan;
import az.millers.hcm.learning.domain.TrainingPlanItem;

/** DTOs for the M157 Training Plan feature (§8.14.2). */
public final class TrainingPlanDtos {

    private TrainingPlanDtos() {}

    public record TrainingPlanRequest(
            @NotBlank @Size(max = 300) String name,
            @Size(max = 2000) String description,
            /** DEPARTMENT | ANNUAL | COMPLIANCE | CAREER_PATH */
            @NotNull String planType,
            UUID orgUnitId,
            Short fiscalYear,
            LocalDate deadline,
            UUID ownerId) {}

    public record TrainingPlanItemRequest(
            @NotNull UUID courseId,
            LocalDate dueDate,
            UUID positionId,
            @Size(max = 1000) String notes,
            int sortOrder) {}

    public record TrainingPlanItemResponse(
            UUID id,
            UUID courseId,
            String courseTitle,
            String courseCode,
            LocalDate dueDate,
            UUID positionId,
            String notes,
            int sortOrder) {

        public static TrainingPlanItemResponse from(TrainingPlanItem item, String courseTitle, String courseCode) {
            return new TrainingPlanItemResponse(
                    item.getId(),
                    item.getCourseId(),
                    courseTitle,
                    courseCode,
                    item.getDueDate(),
                    item.getPositionId(),
                    item.getNotes(),
                    item.getSortOrder());
        }
    }

    public record TrainingPlanResponse(
            UUID id,
            String planNo,
            String name,
            String description,
            String planType,
            String status,
            UUID orgUnitId,
            Short fiscalYear,
            LocalDate deadline,
            UUID ownerId,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime activatedAt,
            OffsetDateTime completedAt,
            int enrolledCount,
            int completedCount,
            List<TrainingPlanItemResponse> items) {

        public static TrainingPlanResponse from(TrainingPlan p, List<TrainingPlanItemResponse> items) {
            return new TrainingPlanResponse(
                    p.getId(),
                    p.getPlanNo(),
                    p.getName(),
                    p.getDescription(),
                    p.getPlanType(),
                    p.getStatus(),
                    p.getOrgUnitId(),
                    p.getFiscalYear(),
                    p.getDeadline(),
                    p.getOwnerId(),
                    p.getCreatedBy(),
                    p.getCreatedAt(),
                    p.getActivatedAt(),
                    p.getCompletedAt(),
                    p.getEnrolledCount(),
                    p.getCompletedCount(),
                    items);
        }
    }

    public record EnrollAllResult(int enrolled, int skipped) {}
}
