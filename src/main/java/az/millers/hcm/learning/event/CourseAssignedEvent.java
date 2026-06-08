package az.millers.hcm.learning.event;

import java.util.UUID;

/**
 * Published by {@link az.millers.hcm.learning.service.EnrollmentService}
 * when an employee is enrolled in a course (M182 / PRD §8.14.7).
 *
 * @param employeeId    the enrolled employee
 * @param courseId      the course they were enrolled in
 * @param courseName    human-readable course title (avoids a DB lookup in the listener)
 * @param enrollmentId  the new enrollment record
 */
public record CourseAssignedEvent(UUID employeeId, UUID courseId,
                                   String courseName, UUID enrollmentId) {}
