package az.millers.hcm.learning.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published by {@link az.millers.hcm.learning.service.EnrollmentService}
 * when a quiz attempt is graded PASSED and the enrollment status transitions
 * to {@code EnrollmentStatus.PASSED}.
 *
 * <p>Listeners (e.g. {@link az.millers.hcm.performance.service.GoalAutoRatingService})
 * subscribe via {@code @EventListener} to act on the completed enrollment
 * without creating a hard compile-time dependency from the learning module to
 * the performance module (M49).
 *
 * @param employeeId       the employee who passed the course
 * @param courseId         the course that was passed
 * @param bestScorePercent the employee's best quiz score (0–100, two decimals)
 */
public record CoursePassedEvent(UUID employeeId, UUID courseId, BigDecimal bestScorePercent) {}
