package az.millers.hcm.learning.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published by {@link az.millers.hcm.learning.service.EnrollmentService}
 * when all quiz attempts are exhausted and the enrollment transitions to
 * {@code EnrollmentStatus.FAILED} (M182 / PRD §8.14.7).
 *
 * @param employeeId       the employee who exhausted their attempts
 * @param courseId         the course they failed
 * @param courseName       human-readable course title
 * @param bestScorePercent their best score across all attempts
 */
public record CourseFailedEvent(UUID employeeId, UUID courseId,
                                 String courseName, BigDecimal bestScorePercent) {}
