package az.millers.hcm.performance.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.performance.domain.CycleStatus;
import az.millers.hcm.performance.domain.CycleType;
import az.millers.hcm.performance.domain.ReviewCycle;

public record ReviewCycleResponse(
        UUID id,
        String code,
        String name,
        CycleType cycleType,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate selfReviewDue,
        LocalDate managerReviewDue,
        LocalDate finalDue,
        CycleStatus status,
        String description,
        Map<String, Object> ratingScale,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy) {

    public static ReviewCycleResponse from(ReviewCycle c) {
        return new ReviewCycleResponse(
                c.getId(), c.getCode(), c.getName(), c.getCycleType(),
                c.getPeriodStart(), c.getPeriodEnd(),
                c.getSelfReviewDue(), c.getManagerReviewDue(), c.getFinalDue(),
                c.getStatus(), c.getDescription(), c.getRatingScale(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getCreatedBy(), c.getUpdatedBy());
    }
}
