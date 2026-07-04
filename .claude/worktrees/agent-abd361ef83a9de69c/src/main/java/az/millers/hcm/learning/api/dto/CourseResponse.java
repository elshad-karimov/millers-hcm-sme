package az.millers.hcm.learning.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.learning.domain.Course;
import az.millers.hcm.learning.domain.CourseCategory;
import az.millers.hcm.learning.domain.CourseStatus;

public record CourseResponse(
        UUID id,
        String courseNo,
        String code,
        String title,
        String description,
        String contentMarkdown,
        CourseCategory category,
        BigDecimal durationHours,
        boolean mandatory,
        int passingScore,
        int maxAttempts,
        CourseStatus status,
        UUID instructorId,
        Integer validForMonths,
        String coverUrl,
        OffsetDateTime publishedAt,
        OffsetDateTime archivedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy) {

    public static CourseResponse from(Course c) {
        return new CourseResponse(
                c.getId(), c.getCourseNo(), c.getCode(), c.getTitle(),
                c.getDescription(), c.getContentMarkdown(), c.getCategory(),
                c.getDurationHours(), c.isMandatory(), c.getPassingScore(),
                c.getMaxAttempts(), c.getStatus(), c.getInstructorId(),
                c.getValidForMonths(), c.getCoverUrl(),
                c.getPublishedAt(), c.getArchivedAt(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getCreatedBy(), c.getUpdatedBy());
    }
}
