package az.millers.hcm.policy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.policy.event.PolicyPublishedEvent;

/**
 * Notifies all ACTIVE employees when a policy requiring acknowledgement
 * is published (M192 / PRD §8.17 Policy Library).
 *
 * <p>{@code @Async} ensures notification failures never roll back the
 * policy publish transaction. The TRANSACTIONAL category is used because
 * policy acknowledgements are mandatory — employees cannot opt out.
 */
@Component
public class PolicyAckNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PolicyAckNotificationListener.class);

    private final EmployeeRepository employees;
    private final NotificationService notifications;

    public PolicyAckNotificationListener(EmployeeRepository employees,
                                          NotificationService notifications) {
        this.employees = employees;
        this.notifications = notifications;
    }

    @Async
    @EventListener
    public void onPolicyPublished(PolicyPublishedEvent event) {
        String title = "New policy requires your acknowledgement: " + event.title();
        String body  = "A new company policy \"" + event.title() + "\" (v" + event.version()
                + ") has been published and requires your acknowledgement. "
                + "Please review and acknowledge it in the Policy Library.";

        int notified = 0;
        for (Employee emp : employees.findAllByEmploymentStatus(EmploymentStatus.ACTIVE)) {
            String username = emp.getUsername();
            if (username == null || username.isBlank()) continue;
            try {
                notifications.notifyAll(
                        NotificationCategory.TRANSACTIONAL,
                        username, title, body,
                        "POLICY", "PolicyDocument", event.policyId().toString());
                notified++;
            } catch (Exception ex) {
                log.warn("PolicyAckNotificationListener: failed to notify {} for policy {}: {}",
                        username, event.code(), ex.getMessage());
            }
        }
        log.info("PolicyAckNotificationListener: notified {} employee(s) about policy {} v{}",
                notified, event.code(), event.version());
    }
}
