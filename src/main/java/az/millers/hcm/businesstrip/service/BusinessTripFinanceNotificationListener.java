package az.millers.hcm.businesstrip.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import az.millers.hcm.businesstrip.event.BusinessTripApprovedEvent;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;

/**
 * Notifies Finance when a business-trip advance is approved so it can be
 * disbursed (M199 / PRD §8.6.7 AC: "notifies Finance for disbursement").
 *
 * <p>Finance recipients are configured via
 * {@code hcm.notifications.businesstrip.finance-recipients} (comma-separated
 * Keycloak usernames). If the list is empty the notification is silently
 * skipped.
 *
 * <p>{@code @Async} ensures a notification failure never rolls back the
 * upstream approval transaction.
 */
@Component
public class BusinessTripFinanceNotificationListener {

    private static final Logger log =
            LoggerFactory.getLogger(BusinessTripFinanceNotificationListener.class);

    private static final String MODULE = "BUSINESS_TRIP";

    private final NotificationService notifications;
    private final EmployeeRepository employees;

    /** Comma-separated Keycloak usernames of the Finance team. */
    @Value("${hcm.notifications.businesstrip.finance-recipients:}")
    private String financeRecipients;

    public BusinessTripFinanceNotificationListener(NotificationService notifications,
                                                    EmployeeRepository employees) {
        this.notifications = notifications;
        this.employees = employees;
    }

    @Async
    @EventListener
    public void onTripApproved(BusinessTripApprovedEvent event) {
        List<String> recipients = parseRecipients(financeRecipients);
        if (recipients.isEmpty()) return;

        String employeeName = employees.findById(event.employeeId())
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElse("(employee)");

        String destination = event.destinationCountry() != null
                ? event.destinationCity() + ", " + event.destinationCountry()
                : event.destinationCity();

        String title = "Business trip advance approved: " + event.tripNo();
        String body = employeeName + " has an approved business trip ("
                + event.tripNo() + ") to " + destination
                + " from " + event.startDate() + " to " + event.endDate()
                + " (" + event.totalDays() + " day(s))."
                + (event.approvedAdvance() != null && event.approvedAdvance().signum() > 0
                        ? " Advance to disburse: " + event.approvedAdvance().toPlainString()
                                + " " + event.currency() + "."
                        : "");

        for (String recipient : recipients) {
            try {
                notifications.notifyAll(
                        NotificationCategory.TRANSACTIONAL,
                        recipient, title, body,
                        MODULE, "BusinessTripRequest", event.tripId().toString());
            } catch (Exception ex) {
                log.warn("BusinessTripFinanceNotificationListener: failed to notify {} for {}: {}",
                        recipient, event.tripNo(), ex.getMessage());
            }
        }
    }

    private static List<String> parseRecipients(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
