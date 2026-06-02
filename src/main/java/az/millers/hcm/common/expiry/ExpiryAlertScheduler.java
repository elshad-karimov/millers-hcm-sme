package az.millers.hcm.common.expiry;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;

/**
 * Single daily scheduler that drives expiry-reminder notifications for every
 * {@link ExpiryAlertSource} Spring-registered bean (M61).
 *
 * <p>The scheduler is deliberately the <em>only</em> place expiry alert logic
 * lives. Identification documents, employment contracts, professional
 * certifications, and health certificates each plug in by implementing
 * {@link ExpiryTrackable} and exposing an {@link ExpiryAlertSource} bean.
 * Adding a new expiry-bearing entity requires zero changes here.
 *
 * <p>Alert windows are {90, 60, 30, 14, 7, 0} days ahead of {@code today}.
 * For each window the scheduler invokes every source's
 * {@link ExpiryAlertSource#findExpiringOn(LocalDate)} method (one query per
 * source per window — six fast queries each daily run). Each returned row
 * fans out to:
 * <ul>
 *   <li>the document's owning employee (via {@code Employee.username})</li>
 *   <li>the employee's direct manager (if any)</li>
 * </ul>
 * Delivery goes through {@link NotificationService#notifyAll} so IN_APP, EMAIL
 * and PUSH channels are all populated transparently — no duplicated
 * notification plumbing per entity type.
 *
 * <p>Runs at 06:00 Europe/Baku daily by default. The cron and the alert
 * windows are externalised so individual tenants can tune them without
 * recompiling.
 */
@Component
public class ExpiryAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryAlertScheduler.class);

    /** Alert windows in days. Order is informational only — each is processed independently. */
    private static final int[] DEFAULT_DAYS_AHEAD = {90, 60, 30, 14, 7, 0};

    private final List<ExpiryAlertSource> sources;
    private final EmployeeRepository employees;
    private final NotificationService notifications;
    private final int[] daysAhead;

    public ExpiryAlertScheduler(List<ExpiryAlertSource> sources,
                                EmployeeRepository employees,
                                NotificationService notifications,
                                @Value("${hcm.expiry.days-ahead:}") String configuredDays) {
        this.sources = sources;
        this.employees = employees;
        this.notifications = notifications;
        this.daysAhead = parseDays(configuredDays);
    }

    /**
     * Daily walker. {@code 0 0 6 * * *} = 06:00 every day in the Spring task
     * scheduler's local TZ (Europe/Baku in our deployment). The schedule is
     * configurable so test harnesses can stop the cron firing.
     *
     * <p>Idempotency: the underlying {@link NotificationService} de-duplicates
     * by {@code (recipient, entityId, alertWindow)} when configured to (M68
     * will harden that); for now a duplicate fire on the same calendar day
     * would post duplicate in-app entries, which we tolerate during Phase 1.
     */
    @Scheduled(cron = "${hcm.expiry.cron:0 0 6 * * *}")
    public void scanAll() {
        if (sources.isEmpty()) {
            // No expiry-bearing entities registered yet (M61 ships the
            // abstraction; M63+ wire the first concrete sources). Bail out
            // quietly rather than spam the log.
            return;
        }
        LocalDate today = LocalDate.now();
        int totalFired = 0;
        for (int delta : daysAhead) {
            LocalDate target = today.plusDays(delta);
            for (ExpiryAlertSource source : sources) {
                totalFired += fireWindowForSource(source, target, delta);
            }
        }
        log.info("ExpiryAlertScheduler scan complete: {} alerts dispatched across {} source(s)",
                totalFired, sources.size());
    }

    /** Visible-for-testing entry point so a JUnit test can drive a specific date. */
    public int scanFor(LocalDate today) {
        if (sources.isEmpty()) return 0;
        int totalFired = 0;
        for (int delta : daysAhead) {
            LocalDate target = today.plusDays(delta);
            for (ExpiryAlertSource source : sources) {
                totalFired += fireWindowForSource(source, target, delta);
            }
        }
        return totalFired;
    }

    private int fireWindowForSource(ExpiryAlertSource source, LocalDate target, int delta) {
        List<? extends ExpiryTrackable> hits;
        try {
            hits = source.findExpiringOn(target);
        } catch (RuntimeException ex) {
            // One bad source must not poison the whole walk — log and move on.
            log.warn("ExpiryAlertSource {} threw while querying {} (delta {}d): {}",
                    source.getClass().getSimpleName(), target, delta, ex.toString());
            return 0;
        }
        int fired = 0;
        for (ExpiryTrackable item : hits) {
            fired += dispatch(source, item, delta);
        }
        return fired;
    }

    private int dispatch(ExpiryAlertSource source, ExpiryTrackable item, int delta) {
        Employee employee = employees.findById(item.getEmployeeId()).orElse(null);
        if (employee == null) {
            // Orphaned row — log it, but it shouldn't block other alerts.
            log.warn("Expiry source {} returned id {} pointing at missing employee {} — skipping",
                    source.entityName(), item.getId(), item.getEmployeeId());
            return 0;
        }

        String title = buildTitle(item, delta);
        String body  = buildBody(item, employee, delta);

        // Notify the employee themselves (if we have a Keycloak username).
        if (employee.getUsername() != null && !employee.getUsername().isBlank()) {
            notifications.notifyAll(
                    employee.getUsername(),
                    title,
                    body,
                    source.moduleName(),
                    source.entityName(),
                    item.getId().toString());
        }

        // Notify the manager too — they're on the hook for renewal follow-ups.
        // We tolerate a missing manager username (e.g. C-suite with no manager).
        UUID managerId = employee.getManagerId();
        if (managerId != null && !managerId.equals(employee.getId())) {
            employees.findById(managerId).ifPresent(mgr -> {
                if (mgr.getUsername() != null && !mgr.getUsername().isBlank()) {
                    notifications.notifyAll(
                            mgr.getUsername(),
                            title,
                            body + " (Direct report: " + employee.getFirstName()
                                    + " " + employee.getLastName() + ")",
                            source.moduleName(),
                            source.entityName(),
                            item.getId().toString());
                }
            });
        }
        return 1;
    }

    private static String buildTitle(ExpiryTrackable item, int delta) {
        if (delta == 0) {
            return item.getEntityLabel() + " expires today";
        }
        return item.getEntityLabel() + " expires in " + delta + " day" + (delta == 1 ? "" : "s");
    }

    private static String buildBody(ExpiryTrackable item, Employee employee, int delta) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getEntityLabel())
          .append(" \"").append(item.getDisplayName()).append("\" ");
        if (delta == 0) {
            sb.append("expires today (").append(item.getExpiryDate()).append(").");
        } else {
            sb.append("is due to expire on ").append(item.getExpiryDate())
              .append(" — ").append(delta).append(" day")
              .append(delta == 1 ? "" : "s").append(" from now.");
        }
        sb.append(" Please initiate renewal.");
        return sb.toString();
    }

    private static int[] parseDays(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_DAYS_AHEAD;
        }
        String[] parts = configured.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
            if (out[i] < 0) {
                throw new IllegalArgumentException(
                        "hcm.expiry.days-ahead values must be ≥ 0: " + parts[i]);
            }
        }
        return out;
    }
}
