package az.millers.hcm.compbenefits.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.compbenefits.domain.BenefitEnrollment;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.EnrollmentStatus;
import az.millers.hcm.compbenefits.repo.BenefitEnrollmentRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;

/**
 * Nightly scheduler that auto-terminates benefit enrollments for plans that
 * have passed their {@code effectiveTo} date (M191 / PRD §8.15 Benefits).
 *
 * <p>Runs at 02:30 UTC daily. For every {@link BenefitPlan} whose
 * {@code effectiveTo} is non-null and before today, all still-ENROLLED
 * employees are transitioned to TERMINATED and notified.
 *
 * <p>Failures are caught per-enrollment and never abort the batch.
 */
@Component
public class BenefitEnrollmentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BenefitEnrollmentExpiryScheduler.class);

    private final BenefitPlanRepository plans;
    private final BenefitEnrollmentRepository enrollments;
    private final EmployeeRepository employees;
    private final NotificationService notifications;

    public BenefitEnrollmentExpiryScheduler(BenefitPlanRepository plans,
                                              BenefitEnrollmentRepository enrollments,
                                              EmployeeRepository employees,
                                              NotificationService notifications) {
        this.plans = plans;
        this.enrollments = enrollments;
        this.employees = employees;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void terminateExpiredEnrollments() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<BenefitPlan> expiredPlans = plans.findByEffectiveToIsNotNullAndEffectiveToBefore(today);
        if (expiredPlans.isEmpty()) return;

        log.info("BenefitEnrollmentExpiryScheduler: {} plan(s) expired, checking ENROLLED enrollments",
                expiredPlans.size());

        int terminated = 0;
        for (BenefitPlan plan : expiredPlans) {
            List<BenefitEnrollment> active =
                    enrollments.findByPlanIdAndStatus(plan.getId(), EnrollmentStatus.ENROLLED);
            for (BenefitEnrollment e : active) {
                try {
                    e.setStatus(EnrollmentStatus.TERMINATED);
                    e.setEndDate(plan.getEffectiveTo());
                    e.setTerminationReason("Plan expired — auto-terminated by system");
                    e.setTerminatedBy("system");
                    e.setTerminatedAt(OffsetDateTime.now());
                    enrollments.save(e);
                    terminated++;

                    // Notify the employee about the enrollment termination.
                    employees.findById(e.getEmployeeId()).ifPresent(emp -> {
                        String username = emp.getUsername();
                        if (username == null || username.isBlank()) return;
                        String title = "Benefit enrollment ended: " + plan.getName();
                        String body = "Your enrollment in the \"" + plan.getName()
                                + "\" benefit plan has been automatically terminated because "
                                + "the plan expired on " + plan.getEffectiveTo() + ".";
                        try {
                            notifications.notifyAll(
                                    NotificationCategory.BENEFIT_ENROLLMENT,
                                    username, title, body,
                                    "COMP_BENEFITS", "BenefitEnrollment", e.getId().toString());
                        } catch (Exception ex) {
                            log.warn("BenefitEnrollmentExpiryScheduler: failed to notify {} "
                                    + "for enrollment {}: {}", username, e.getId(), ex.getMessage());
                        }
                    });
                } catch (Exception ex) {
                    log.warn("BenefitEnrollmentExpiryScheduler: failed to terminate enrollment {} "
                            + "for plan {}: {}", e.getId(), plan.getCode(), ex.getMessage());
                }
            }
        }
        log.info("BenefitEnrollmentExpiryScheduler: auto-terminated {} enrollment(s)", terminated);
    }
}
