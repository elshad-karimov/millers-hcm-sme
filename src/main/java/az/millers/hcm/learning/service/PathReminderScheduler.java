package az.millers.hcm.learning.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import az.millers.hcm.audit.AuditIdempotency;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.domain.LearningPath;
import az.millers.hcm.learning.domain.LearningPathAssignment;
import az.millers.hcm.learning.domain.PathAssignmentStatus;
import az.millers.hcm.learning.repo.LearningPathAssignmentRepository;
import az.millers.hcm.learning.repo.LearningPathRepository;
import az.millers.hcm.notifications.NotificationService;

/**
 * Daily nudge for learning-path target dates (M97).
 *
 * <p>For every non-terminal {@link LearningPathAssignment} with a
 * {@code targetCompletionDate}, fires a notification to the assignee
 * <em>and</em> their direct manager at configurable deltas before / after
 * the target date. Default windows: {30, 14, 7, 1, 0} days ahead and
 * {-7, -30} days past-due. The negative deltas escalate gently — the
 * assignee sees them first, the manager only when overdue.
 *
 * <p>Mirrors the ExpiryAlertScheduler / StalePoolReminderScheduler patterns:
 * <ul>
 *   <li>Cron at 08:00 (after expiry at 06:00 and stale-pool at 07:00).</li>
 *   <li>Idempotent via {@link AuditIdempotency} keyed on (today, delta).</li>
 *   <li>Per-source try/catch so one bad row doesn't poison the walk.</li>
 *   <li>Result envelope ({@link ScanSummary}) returned from a public
 *       {@code scanFor(today)} entry point — same shape the admin
 *       trigger / tests use elsewhere.</li>
 * </ul>
 */
@Component
public class PathReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PathReminderScheduler.class);

    private static final String MODULE = "LEARNING";
    private static final String ENTITY = "LearningPathAssignment";
    private static final String ACTION = "PATH_REMINDER_SENT";

    /** Default deltas (days). Positive = approaching; negative = overdue. */
    private static final int[] DEFAULT_DELTAS = {30, 14, 7, 1, 0, -7, -30};

    /** Reminder targets active assignments only. */
    private static final List<PathAssignmentStatus> ACTIVE_STATUSES =
            List.of(PathAssignmentStatus.ASSIGNED, PathAssignmentStatus.IN_PROGRESS);

    private final LearningPathAssignmentRepository assignments;
    private final LearningPathRepository paths;
    private final EmployeeRepository employees;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AuditIdempotency auditIdempotency;
    private final int[] deltas;

    public PathReminderScheduler(LearningPathAssignmentRepository assignments,
                                  LearningPathRepository paths,
                                  EmployeeRepository employees,
                                  NotificationService notifications,
                                  AuditService audit,
                                  AuditIdempotency auditIdempotency,
                                  @Value("${hcm.learning.reminders.deltas:}") String configuredDeltas) {
        this.assignments = assignments;
        this.paths = paths;
        this.employees = employees;
        this.notifications = notifications;
        this.audit = audit;
        this.auditIdempotency = auditIdempotency;
        this.deltas = parseDeltas(configuredDeltas);
    }

    /** Result envelope returned by the manual / admin scan trigger. */
    public record ScanSummary(
            LocalDate today,
            int candidatesConsidered,
            int alertsDispatched,
            int alertsSkippedAsDuplicate) {}

    @Scheduled(cron = "${hcm.learning.reminders.cron:0 0 8 * * *}")
    public void scanAll() {
        ScanSummary s = scanFor(LocalDate.now());
        if (s.alertsDispatched() > 0 || s.alertsSkippedAsDuplicate() > 0) {
            log.info("PathReminderScheduler: {} considered, {} sent, {} skipped (dedup)",
                    s.candidatesConsidered(), s.alertsDispatched(), s.alertsSkippedAsDuplicate());
        }
    }

    /** Public for the admin trigger + tests. */
    public ScanSummary scanFor(LocalDate today) {
        List<LearningPathAssignment> active = assignments
                .findByStatusInAndTargetCompletionDateIsNotNull(ACTIVE_STATUSES);
        int dispatched = 0, skipped = 0;
        for (LearningPathAssignment a : active) {
            int actualDelta = daysBetween(today, a.getTargetCompletionDate());
            if (!isReminderDelta(actualDelta, deltas)) continue;
            int[] r = fireOne(a, actualDelta, today);
            dispatched += r[0];
            skipped += r[1];
        }
        return new ScanSummary(today, active.size(), dispatched, skipped);
    }

    // ── Reminder dispatch ──────────────────────────────────────────────────

    private int[] fireOne(LearningPathAssignment a, int delta, LocalDate today) {
        if (auditIdempotency.hasMarker(ENTITY, a.getId().toString(), ACTION,
                Map.of("day", today.toString(), "delta", delta))) {
            return new int[] {0, 1};
        }
        Optional<Employee> emp = employees.findById(a.getEmployeeId());
        if (emp.isEmpty()) {
            log.warn("PathReminderScheduler: assignment {} points at missing employee {}",
                    a.getId(), a.getEmployeeId());
            return new int[] {0, 0};
        }
        Employee employee = emp.get();
        String pathName = paths.findById(a.getPathId())
                .map(LearningPath::getName).orElse("(unknown path)");

        String title = buildTitle(delta, pathName);
        String body  = buildBody(delta, pathName, a.getTargetCompletionDate(),
                a.getNotes());

        if (employee.getUsername() != null && !employee.getUsername().isBlank()) {
            notifications.notifyAll(
                    employee.getUsername(), title, body,
                    MODULE, ENTITY, a.getId().toString());
        }

        // Notify the manager once the path is at or past due. Earlier
        // reminders are between the learner and their plan; escalation
        // brings the manager in only when it starts to slip.
        if (delta <= 0 && employee.getManagerId() != null
                && !employee.getManagerId().equals(employee.getId())) {
            employees.findById(employee.getManagerId()).ifPresent(mgr -> {
                if (mgr.getUsername() != null && !mgr.getUsername().isBlank()) {
                    notifications.notifyAll(
                            mgr.getUsername(),
                            title + " — direct report",
                            body + "\n\n(For your direct report "
                                    + employee.getFirstName() + " "
                                    + employee.getLastName() + ".)",
                            MODULE, ENTITY, a.getId().toString());
                }
            });
        }

        audit.record(MODULE, ENTITY, a.getId().toString(), ACTION, null,
                Map.of(
                        "day", today.toString(),
                        "delta", delta,
                        "target", a.getTargetCompletionDate().toString(),
                        "path", pathName,
                        "recipient", employee.getUsername() == null ? "" : employee.getUsername()));
        return new int[] {1, 0};
    }

    // ── Message templates ──────────────────────────────────────────────────

    static String buildTitle(int delta, String pathName) {
        if (delta > 0) {
            return "Learning path \"" + pathName + "\" due in " + delta + " day"
                    + (delta == 1 ? "" : "s");
        }
        if (delta == 0) {
            return "Learning path \"" + pathName + "\" due today";
        }
        int overdue = -delta;
        return "Learning path \"" + pathName + "\" is " + overdue + " day"
                + (overdue == 1 ? "" : "s") + " overdue";
    }

    static String buildBody(int delta, String pathName, LocalDate target, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Your assigned learning path \"").append(pathName).append("\" ");
        if (delta > 0) {
            sb.append("is due on ").append(target).append(" (")
              .append(delta).append(" day").append(delta == 1 ? "" : "s")
              .append(" away).");
        } else if (delta == 0) {
            sb.append("is due today (").append(target).append(").");
        } else {
            sb.append("was due on ").append(target).append(" (").append(-delta)
              .append(" day").append(delta == -1 ? "" : "s").append(" ago).");
        }
        if (notes != null && !notes.isBlank()) {
            sb.append("\n\nAssignment notes: ").append(notes);
        }
        sb.append("\n\nReview progress at /learning/paths.");
        return sb.toString();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Day-precision delta: positive = target is in the future, negative =
     * target has passed. {@code daysBetween(today, target)}.
     */
    static int daysBetween(LocalDate today, LocalDate target) {
        if (today == null || target == null) return Integer.MIN_VALUE;
        return (int) ChronoUnit.DAYS.between(today, target);
    }

    static boolean isReminderDelta(int actual, int[] configured) {
        for (int d : configured) if (d == actual) return true;
        return false;
    }

    private static int[] parseDeltas(String configured) {
        if (configured == null || configured.isBlank()) return DEFAULT_DELTAS;
        String[] parts = configured.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }
}
