package az.millers.hcm.learning.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.event.CourseAssignedEvent;
import az.millers.hcm.learning.event.CourseFailedEvent;
import az.millers.hcm.learning.event.CoursePassedEvent;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;

/**
 * Sends LMS notifications on course lifecycle events (M182 / PRD §8.14.7).
 *
 * <p>Handled events:
 * <ul>
 *   <li>{@link CourseAssignedEvent} — notifies the enrolled employee.</li>
 *   <li>{@link CoursePassedEvent}   — notifies the employee (pass + certificate)
 *       and their manager.</li>
 *   <li>{@link CourseFailedEvent}   — notifies the employee when all attempts
 *       are exhausted.</li>
 * </ul>
 *
 * <p>Each handler is {@code @Async} so a notification failure can never roll
 * back the upstream business transaction.
 */
@Component
public class LmsNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(LmsNotificationListener.class);
    private static final String MODULE = "LEARNING";

    private final NotificationService notifications;
    private final EmployeeRepository employees;
    private final CourseRepository courses;

    public LmsNotificationListener(NotificationService notifications,
                                    EmployeeRepository employees,
                                    CourseRepository courses) {
        this.notifications = notifications;
        this.employees = employees;
        this.courses = courses;
    }

    // ------------------------------------------------------------------ //
    //  Course assigned                                                    //
    // ------------------------------------------------------------------ //

    @Async
    @EventListener
    public void onCourseAssigned(CourseAssignedEvent event) {
        try {
            Optional<Employee> emp = employees.findById(event.employeeId());
            if (emp.isEmpty() || blank(emp.get().getUsername())) return;

            String title = "New course assigned: " + event.courseName();
            String body  = "You have been enrolled in the course \""
                    + event.courseName()
                    + "\". Open the Learning portal to get started.";
            notifications.notifyAll(
                    NotificationCategory.COURSE_ASSIGNMENT,
                    emp.get().getUsername(), title, body,
                    MODULE, "Enrollment", event.enrollmentId().toString());
        } catch (Exception ex) {
            log.warn("LmsNotificationListener: failed to send assignment notification " +
                    "for course {} / employee {}: {}", event.courseId(), event.employeeId(), ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Course passed                                                      //
    // ------------------------------------------------------------------ //

    @Async
    @EventListener
    public void onCoursePassed(CoursePassedEvent event) {
        try {
            String courseName = courses.findById(event.courseId())
                    .map(c -> c.getTitle()).orElse("(course)");
            Employee emp = employees.findById(event.employeeId()).orElse(null);
            if (emp == null) return;

            // Notify the employee
            if (!blank(emp.getUsername())) {
                String title = "You passed \"" + courseName + "\"!";
                String body  = "Congratulations! You passed the course \""
                        + courseName + "\" with a score of "
                        + event.bestScorePercent() + "%. "
                        + "Your certificate has been issued and is available in the Learning portal.";
                notifications.notifyAll(
                        NotificationCategory.LEARNING_COMPLETION,
                        emp.getUsername(), title, body,
                        MODULE, "Enrollment", event.courseId().toString());
            }

            // Notify the manager
            if (emp.getManagerId() != null && !emp.getManagerId().equals(emp.getId())) {
                employees.findById(emp.getManagerId()).ifPresent(mgr -> {
                    if (blank(mgr.getUsername())) return;
                    String empName = emp.getFirstName() + " " + emp.getLastName();
                    String title = empName + " passed \"" + courseName + "\"";
                    String body  = "Your direct report " + empName
                            + " has successfully completed the course \""
                            + courseName + "\" (score: " + event.bestScorePercent() + "%).";
                    try {
                        notifications.notifyAll(
                                NotificationCategory.LEARNING_COMPLETION,
                                mgr.getUsername(), title, body,
                                MODULE, "Enrollment", event.courseId().toString());
                    } catch (Exception ex) {
                        log.warn("LmsNotificationListener: failed to notify manager {} " +
                                "for course pass: {}", mgr.getId(), ex.getMessage());
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("LmsNotificationListener: failed to process CoursePassedEvent for " +
                    "course {} / employee {}: {}", event.courseId(), event.employeeId(), ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Course failed                                                      //
    // ------------------------------------------------------------------ //

    @Async
    @EventListener
    public void onCourseFailed(CourseFailedEvent event) {
        try {
            Optional<Employee> emp = employees.findById(event.employeeId());
            if (emp.isEmpty() || blank(emp.get().getUsername())) return;

            String title = "Course failed: " + event.courseName();
            String body  = "Unfortunately you did not pass the course \""
                    + event.courseName() + "\" (best score: "
                    + (event.bestScorePercent() != null ? event.bestScorePercent() + "%" : "—")
                    + "). All attempts have been used. "
                    + "Please contact your manager or HR to arrange a re-enrolment if required.";
            notifications.notifyAll(
                    NotificationCategory.LEARNING_COMPLETION,
                    emp.get().getUsername(), title, body,
                    MODULE, "Enrollment", event.courseId().toString());
        } catch (Exception ex) {
            log.warn("LmsNotificationListener: failed to send failure notification " +
                    "for course {} / employee {}: {}", event.courseId(), event.employeeId(), ex.getMessage());
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
