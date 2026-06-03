package az.millers.hcm.common.expiry;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import az.millers.hcm.audit.AuditIdempotency;
import az.millers.hcm.audit.AuditService;
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
 * <p><strong>Idempotency (M68):</strong> every dispatched alert journals an
 * {@code EXPIRY_ALERT_SENT} audit row keyed on
 * {@code (entityName, entityId, today, deltaDays)}. Re-running the same scan
 * on the same day is a no-op — the marker check skips already-alerted rows.
 * The pattern mirrors {@code LeaveAccrualService.alreadyAccrued}; same
 * tradeoff (one audit-log scan per item) for the same reason (auditable
 * idempotency without a dedicated alert-log table).
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

    /** Module name for audit + notification cross-reference of alert events. */
    private static final String AUDIT_ACTION = "EXPIRY_ALERT_SENT";

    private final List<ExpiryAlertSource> sources;
    private final EmployeeRepository employees;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AuditIdempotency auditIdempotency;
    private final int[] daysAhead;

    public ExpiryAlertScheduler(List<ExpiryAlertSource> sources,
                                EmployeeRepository employees,
                                NotificationService notifications,
                                AuditService audit,
                                AuditIdempotency auditIdempotency,
                                @Value("${hcm.expiry.days-ahead:}") String configuredDays) {
        this.sources = sources;
        this.employees = employees;
        this.notifications = notifications;
        this.audit = audit;
        this.auditIdempotency = auditIdempotency;
        this.daysAhead = parseDays(configuredDays);
    }

    /** Result envelope returned by the manual / admin scan trigger (M68). */
    public record ScanSummary(
            LocalDate today,
            int sourceCount,
            int alertsDispatched,
            int alertsSkippedAsDuplicate) {}

    /**
     * Daily walker. {@code 0 0 6 * * *} = 06:00 every day in the Spring task
     * scheduler's local TZ (Europe/Baku in our deployment).
     */
    @Scheduled(cron = "${hcm.expiry.cron:0 0 6 * * *}")
    public void scanAll() {
        if (sources.isEmpty()) {
            // No expiry-bearing entities registered yet. Bail quietly.
            return;
        }
        ScanSummary summary = scanFor(LocalDate.now());
        log.info("ExpiryAlertScheduler scan complete: {} alerts dispatched, {} skipped (duplicate) across {} source(s)",
                summary.alertsDispatched(), summary.alertsSkippedAsDuplicate(), summary.sourceCount());
    }

    /**
     * Entry point for the admin trigger and JUnit tests. Drives a specific
     * date through the same dispatch logic the cron uses.
     */
    public ScanSummary scanFor(LocalDate today) {
        if (sources.isEmpty()) return new ScanSummary(today, 0, 0, 0);
        int dispatched = 0;
        int skipped = 0;
        for (int delta : daysAhead) {
            LocalDate target = today.plusDays(delta);
            for (ExpiryAlertSource source : sources) {
                int[] result = fireWindowForSource(source, target, delta, today);
                dispatched += result[0];
                skipped += result[1];
            }
        }
        return new ScanSummary(today, sources.size(), dispatched, skipped);
    }

    /**
     * @return {@code [dispatched, skipped]} so the caller can aggregate
     *         per-source totals without hidden state.
     */
    private int[] fireWindowForSource(ExpiryAlertSource source, LocalDate target,
                                       int delta, LocalDate today) {
        List<? extends ExpiryTrackable> hits;
        try {
            hits = source.findExpiringOn(target);
        } catch (RuntimeException ex) {
            // One bad source must not poison the whole walk — log and move on.
            log.warn("ExpiryAlertSource {} threw while querying {} (delta {}d): {}",
                    source.getClass().getSimpleName(), target, delta, ex.toString());
            return new int[] {0, 0};
        }
        int dispatched = 0;
        int skipped = 0;
        for (ExpiryTrackable item : hits) {
            if (alreadyAlerted(source, item, delta, today)) {
                skipped++;
                continue;
            }
            dispatched += dispatch(source, item, delta, today);
        }
        return new int[] {dispatched, skipped};
    }

    /**
     * Audit-log-backed dedup check. Delegates to the shared
     * {@link AuditIdempotency} helper so all schedulers share one
     * regex-over-JSON implementation (M90).
     */
    private boolean alreadyAlerted(ExpiryAlertSource source, ExpiryTrackable item,
                                    int delta, LocalDate today) {
        return auditIdempotency.hasMarker(
                source.entityName(), item.getId().toString(), AUDIT_ACTION,
                Map.of("day", today.toString(), "delta", delta));
    }

    private int dispatch(ExpiryAlertSource source, ExpiryTrackable item, int delta,
                          LocalDate today) {
        Employee employee = employees.findById(item.getEmployeeId()).orElse(null);
        if (employee == null) {
            log.warn("Expiry source {} returned id {} pointing at missing employee {} — skipping",
                    source.entityName(), item.getId(), item.getEmployeeId());
            return 0;
        }

        String title = buildTitle(item, delta);
        String body  = buildBody(item, delta);

        if (employee.getUsername() != null && !employee.getUsername().isBlank()) {
            notifications.notifyAll(
                    employee.getUsername(), title, body,
                    source.moduleName(), source.entityName(), item.getId().toString());
        }

        UUID managerId = employee.getManagerId();
        if (managerId != null && !managerId.equals(employee.getId())) {
            employees.findById(managerId).ifPresent(mgr -> {
                if (mgr.getUsername() != null && !mgr.getUsername().isBlank()) {
                    notifications.notifyAll(
                            mgr.getUsername(), title,
                            body + " (Direct report: " + employee.getFirstName()
                                    + " " + employee.getLastName() + ")",
                            source.moduleName(), source.entityName(), item.getId().toString());
                }
            });
        }

        // Audit marker — what the alreadyAlerted check looks for on the next run.
        audit.record(source.moduleName(), source.entityName(), item.getId().toString(),
                AUDIT_ACTION, null,
                Map.of(
                        "day", today.toString(),
                        "delta", delta,
                        "expiryDate", item.getExpiryDate() == null ? "" : item.getExpiryDate().toString(),
                        "label", item.getEntityLabel(),
                        "recipient", employee.getUsername() == null ? "" : employee.getUsername()));
        return 1;
    }

    private static String buildTitle(ExpiryTrackable item, int delta) {
        if (delta == 0) {
            return item.getEntityLabel() + " expires today";
        }
        return item.getEntityLabel() + " expires in " + delta + " day" + (delta == 1 ? "" : "s");
    }

    private static String buildBody(ExpiryTrackable item, int delta) {
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

    /** Source list — useful for the admin-trigger response and tests. */
    public List<ExpiryAlertSource> getSources() {
        return sources;
    }
}
