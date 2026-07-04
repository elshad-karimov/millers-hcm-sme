package az.millers.hcm.lifecycle.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.event.TerminationProcessedEvent;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;

/**
 * Sends termination notifications when a termination is processed
 * (PRD §8.11.6 AC: notifies IT, Finance, and the line manager).
 *
 * <p>The IT and Finance recipient usernames are configurable via
 * {@code hcm.notifications.termination.it-recipients} and
 * {@code hcm.notifications.termination.finance-recipients} (comma-separated).
 * If empty, those channels are silently skipped.
 *
 * <p>All handlers are {@code @Async} so a notification failure can never
 * roll back the upstream termination transaction.
 */
@Component
public class TerminationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TerminationNotificationListener.class);
    private static final String MODULE = "LIFECYCLE";

    private final NotificationService notifications;
    private final EmployeeRepository employees;

    /** Comma-separated Keycloak usernames of the IT team inbox(es). */
    @Value("${hcm.notifications.termination.it-recipients:}")
    private String itRecipients;

    /** Comma-separated Keycloak usernames of the Finance team inbox(es). */
    @Value("${hcm.notifications.termination.finance-recipients:}")
    private String financeRecipients;

    public TerminationNotificationListener(NotificationService notifications,
                                            EmployeeRepository employees) {
        this.notifications = notifications;
        this.employees = employees;
    }

    @Async
    @EventListener
    public void onTerminationProcessed(TerminationProcessedEvent event) {
        String title = "Termination processed: " + event.employeeName()
                + " (" + event.terminationNo() + ")";
        String baseBody = event.employeeName() + " (" + event.employeeNo()
                + ") has been terminated effective " + event.effectiveDate()
                + " — reason: " + event.reasonCode() + ".";

        // Notify the line manager
        if (event.managerId() != null) {
            try {
                employees.findById(event.managerId()).ifPresent(mgr -> {
                    if (blank(mgr.getUsername())) return;
                    String body = "Your direct report " + baseBody
                            + " Please complete any outstanding clearances in the Termination portal.";
                    try {
                        notifications.notifyAll(
                                NotificationCategory.TERMINATION_NOTICE,
                                mgr.getUsername(), title, body,
                                MODULE, "TerminationRequest", event.terminationId().toString());
                    } catch (Exception ex) {
                        log.warn("TerminationNotificationListener: failed to notify manager {} for {}: {}",
                                mgr.getId(), event.terminationNo(), ex.getMessage());
                    }
                });
            } catch (Exception ex) {
                log.warn("TerminationNotificationListener: error loading manager for {}: {}",
                        event.terminationNo(), ex.getMessage());
            }
        }

        // Notify IT recipients
        String itBody = "Employee " + baseBody
                + " Please revoke system access and reclaim equipment per the IT offboarding checklist.";
        notifyRecipients(parseRecipients(itRecipients), title, itBody, event.terminationId().toString(), "IT");

        // Notify Finance recipients
        String financeBody = "Employee " + baseBody
                + " Please process the final settlement payroll run and close open expense claims.";
        notifyRecipients(parseRecipients(financeRecipients), title, financeBody,
                event.terminationId().toString(), "Finance");
    }

    private void notifyRecipients(List<String> recipients, String title, String body,
                                   String entityId, String team) {
        for (String username : recipients) {
            try {
                notifications.notifyAll(
                        NotificationCategory.TERMINATION_NOTICE,
                        username, title, body,
                        MODULE, "TerminationRequest", entityId);
            } catch (Exception ex) {
                log.warn("TerminationNotificationListener: failed to notify {} team user {}: {}",
                        team, username, ex.getMessage());
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

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
