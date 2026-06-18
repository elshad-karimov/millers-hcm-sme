package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.email.EmailService;
import az.millers.hcm.lifecycle.domain.ChecklistAssignment;
import az.millers.hcm.lifecycle.domain.ChecklistAssignmentStatus;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.ContractStatus;
import az.millers.hcm.lifecycle.domain.EmploymentContract;
import az.millers.hcm.lifecycle.event.ChecklistAssignmentCompletedEvent;
import az.millers.hcm.lifecycle.event.ChecklistStartedEvent;
import az.millers.hcm.lifecycle.repo.ChecklistAssignmentRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTaskStatusRepository;
import az.millers.hcm.lifecycle.repo.EmploymentContractRepository;

/**
 * M308 — Phase D.1: email notifications for key onboarding lifecycle events.
 *
 * <ul>
 *   <li>Onboarding <em>started</em> → email manager of the new hire.
 *   <li>Onboarding <em>completed</em> → email HR + manager; include active
 *       probation end-date if the employee's contract has one.
 *   <li>Daily overdue digest (weekdays 09:00) → email HR with a summary of
 *       onboarding assignments that still have pending tasks after
 *       {@code hcm.notifications.onboarding.overdue-threshold-days} days.
 * </ul>
 *
 * <p><strong>Tx pattern:</strong> both event listeners run AFTER_COMMIT; each
 * side-effect is wrapped in a fresh REQUIRES_NEW transaction so that email
 * failures don't roll back any listener work and so that the tx is actually
 * visible to JPA queries inside (the M303 lesson — REQUIRED joins the
 * already-committed tx and writes are silently discarded).
 */
@Service
public class OnboardingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingNotificationService.class);

    private final EmployeeRepository employees;
    private final EmploymentContractRepository contracts;
    private final ChecklistAssignmentRepository assignments;
    private final ChecklistTaskStatusRepository taskStatuses;
    private final EmailService email;
    private final TransactionTemplate requiresNew;

    @Value("${hcm.notifications.onboarding.hr-email:}")
    private String hrEmail;

    @Value("${hcm.notifications.onboarding.overdue-threshold-days:14}")
    private int overdueThresholdDays;

    public OnboardingNotificationService(EmployeeRepository employees,
                                         EmploymentContractRepository contracts,
                                         ChecklistAssignmentRepository assignments,
                                         ChecklistTaskStatusRepository taskStatuses,
                                         EmailService email,
                                         PlatformTransactionManager txManager) {
        this.employees = employees;
        this.contracts = contracts;
        this.assignments = assignments;
        this.taskStatuses = taskStatuses;
        this.email = email;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── Event listeners ──────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOnboardingStarted(ChecklistStartedEvent event) {
        if (event.flowType() != ChecklistFlowType.ONBOARDING) return;
        requiresNew.execute(status -> {
            notifyManagerOnStart(event.employeeId());
            return null;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOnboardingCompleted(ChecklistAssignmentCompletedEvent event) {
        if (event.flowType() != ChecklistFlowType.ONBOARDING) return;
        requiresNew.execute(status -> {
            notifyOnComplete(event.employeeId());
            return null;
        });
    }

    // ── Scheduled overdue digest ─────────────────────────────────────────────

    @Scheduled(cron = "${hcm.notifications.onboarding.overdue-cron:0 0 9 * * MON-FRI}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendOverdueDigest() {
        if (hrEmail == null || hrEmail.isBlank()) return;

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(overdueThresholdDays);
        List<ChecklistAssignment> stale = assignments
                .findByStatusAndFlowTypeOrderByStartedAtDesc(ChecklistAssignmentStatus.IN_PROGRESS,
                        ChecklistFlowType.ONBOARDING)
                .stream()
                .filter(a -> a.getStartedAt() != null && a.getStartedAt().isBefore(cutoff))
                .toList();

        if (stale.isEmpty()) return;

        StringBuilder body = new StringBuilder(
                "<h2>Overdue onboarding assignments (")
                .append(overdueThresholdDays).append("+ days with pending tasks)</h2><ul>");
        int count = 0;
        for (ChecklistAssignment a : stale) {
            long pending = taskStatuses.findByAssignmentIdOrderByStepOrderAsc(a.getId())
                    .stream()
                    .filter(t -> t.isRequired()
                            && t.getStatus() != ChecklistTaskStatusValue.DONE
                            && t.getStatus() != ChecklistTaskStatusValue.SKIPPED)
                    .count();
            if (pending == 0) continue;
            count++;
            String name = resolveEmployeeName(a.getEmployeeId());
            long daysOpen = java.time.temporal.ChronoUnit.DAYS.between(
                    a.getStartedAt().toLocalDate(), LocalDate.now());
            body.append("<li><strong>").append(htmlEscape(name)).append("</strong> — ")
                    .append(pending).append(" pending task(s), open ").append(daysOpen)
                    .append(" days</li>");
        }
        body.append("</ul>");

        if (count == 0) return;

        email.sendSimple(hrEmail,
                "Onboarding overdue digest — " + count + " hire(s) need attention",
                body.toString());
        log.info("Sent onboarding overdue digest: {} assignment(s)", count);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void notifyManagerOnStart(UUID employeeId) {
        Employee hire = employees.findById(employeeId).orElse(null);
        if (hire == null) return;
        if (hire.getManagerId() == null) return;
        Employee manager = employees.findById(hire.getManagerId()).orElse(null);
        if (manager == null || isBlank(manager.getWorkEmail())) return;

        String hireName = fullName(hire);
        String joinDate = hire.getHireDate() != null ? hire.getHireDate().toString() : "—";
        String body = "<p>Hi " + htmlEscape(fullName(manager)) + ",</p>"
                + "<p><strong>" + htmlEscape(hireName) + "</strong> has started their onboarding journey"
                + " (join date: " + joinDate + ").</p>"
                + "<p>You can track progress in the Millers HCM Onboarding Console.</p>";

        email.sendSimple(manager.getWorkEmail(),
                "Onboarding started: " + hireName,
                body);
        log.info("Notified manager {} that {} started onboarding", manager.getWorkEmail(), hireName);
    }

    private void notifyOnComplete(UUID employeeId) {
        Employee hire = employees.findById(employeeId).orElse(null);
        if (hire == null) return;
        String hireName = fullName(hire);

        String probationNote = buildProbationNote(employeeId);

        String body = "<p><strong>" + htmlEscape(hireName) + "</strong> has completed all"
                + " onboarding tasks.</p>"
                + (probationNote.isEmpty() ? "" : probationNote)
                + "<p>Please review their profile in Millers HCM.</p>";

        if (!isBlank(hrEmail)) {
            email.sendSimple(hrEmail, "Onboarding complete: " + hireName, body);
        }

        if (hire.getManagerId() != null) {
            employees.findById(hire.getManagerId()).ifPresent(mgr -> {
                if (!isBlank(mgr.getWorkEmail())) {
                    email.sendSimple(mgr.getWorkEmail(),
                            "Onboarding complete: " + hireName, body);
                    log.info("Notified manager {} that {} completed onboarding",
                            mgr.getWorkEmail(), hireName);
                }
            });
        }
    }

    private String buildProbationNote(UUID employeeId) {
        return contracts.findByEmployeeIdAndStatus(employeeId, ContractStatus.ACTIVE)
                .filter(c -> c.getProbationEndDate() != null
                        && c.getProbationEndDate().isAfter(LocalDate.now()))
                .map(c -> "<p><em>Probation period is now active and runs until "
                        + c.getProbationEndDate() + ". A probation review is already scheduled.</em></p>")
                .orElse("");
    }

    private String resolveEmployeeName(UUID employeeId) {
        return employees.findById(employeeId)
                .map(this::fullName)
                .orElse(employeeId.toString());
    }

    private String fullName(Employee e) {
        return ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
