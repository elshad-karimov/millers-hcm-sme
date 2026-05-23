package az.millers.hcm.learning.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.learning.domain.EnrolledVia;
import az.millers.hcm.learning.domain.Enrollment;
import az.millers.hcm.learning.domain.EnrollmentStatus;

public record EnrollmentResponse(
        UUID id,
        String enrollmentNo,
        UUID courseId,
        UUID employeeId,
        EnrollmentStatus status,
        OffsetDateTime enrolledAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        LocalDate dueDate,
        int attemptsUsed,
        BigDecimal bestScorePercent,
        BigDecimal lastScorePercent,
        OffsetDateTime lastAttemptAt,
        EnrolledVia enrolledVia,
        String assignedBy) {

    public static EnrollmentResponse from(Enrollment e) {
        return new EnrollmentResponse(
                e.getId(), e.getEnrollmentNo(), e.getCourseId(), e.getEmployeeId(),
                e.getStatus(), e.getEnrolledAt(), e.getStartedAt(), e.getCompletedAt(),
                e.getDueDate(), e.getAttemptsUsed(), e.getBestScorePercent(),
                e.getLastScorePercent(), e.getLastAttemptAt(),
                e.getEnrolledVia(), e.getAssignedBy());
    }
}
