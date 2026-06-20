package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
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
import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.domain.OffboardingCaseStatus;
import az.millers.hcm.lifecycle.event.OffboardingCaseActivatedEvent;
import az.millers.hcm.lifecycle.event.OffboardingSettlementApprovedEvent;
import az.millers.hcm.lifecycle.repo.OffboardingCaseRepository;

/**
 * M324 — Offboarding PRD §39: email notifications for key offboarding events.
 *
 * <ul>
 *   <li>Case activated → email HR + employee's manager.
 *   <li>Settlement approved → email HR.
 *   <li>Daily LWD reminder (weekdays 08:00) → email HR for employees whose LWD
 *       is within the configured look-ahead window and whose case is not yet COMPLETED.
 * </ul>
 */
@Service
public class OffboardingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OffboardingNotificationService.class);

    private final OffboardingCaseRepository caseRepo;
    private final EmployeeRepository employees;
    private final EmailService email;
    private final TransactionTemplate requiresNew;

    @Value("${hcm.notifications.offboarding.hr-email:}")
    private String hrEmail;

    @Value("${hcm.notifications.offboarding.lwd-reminder-days:7}")
    private int lwdReminderDays;

    public OffboardingNotificationService(OffboardingCaseRepository caseRepo,
                                          EmployeeRepository employees,
                                          EmailService email,
                                          PlatformTransactionManager txManager) {
        this.caseRepo = caseRepo;
        this.employees = employees;
        this.email = email;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── Event listeners ──────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCaseActivated(OffboardingCaseActivatedEvent event) {
        requiresNew.execute(s -> {
            notifyCaseStarted(event.caseId(), event.employeeId());
            return null;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettlementApproved(OffboardingSettlementApprovedEvent event) {
        if (hrEmail == null || hrEmail.isBlank()) return;
        requiresNew.execute(s -> {
            Employee emp = employees.findById(event.employeeId()).orElse(null);
            String empName = emp != null ? fullName(emp) : event.employeeId().toString();
            String body = "<p>Final settlement <strong>" + htmlEscape(event.settlementNo())
                    + "</strong> for <strong>" + htmlEscape(empName) + "</strong> has been approved.</p>"
                    + "<p>Please proceed with payment processing in Millers HCM.</p>";
            email.sendSimple(hrEmail, "Settlement approved: " + empName + " (" + event.settlementNo() + ")", body);
            log.info("Notified HR of settlement approval {} for employee {}", event.settlementNo(), empName);
            return null;
        });
    }

    // ── Scheduled: LWD reminder ──────────────────────────────────────────────

    @Scheduled(cron = "${hcm.notifications.offboarding.lwd-reminder-cron:0 0 8 * * MON-FRI}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendLwdReminderDigest() {
        if (hrEmail == null || hrEmail.isBlank()) return;

        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(lwdReminderDays);

        List<OffboardingCase> upcoming = caseRepo
                .findByLastWorkingDateBetweenAndCaseStatusNotIn(today, horizon,
                        List.of(OffboardingCaseStatus.COMPLETED, OffboardingCaseStatus.CANCELLED,
                                OffboardingCaseStatus.REVERSED));

        if (upcoming.isEmpty()) return;

        StringBuilder body = new StringBuilder(
                "<h2>Upcoming last working days — next " + lwdReminderDays + " day(s)</h2><ul>");
        for (OffboardingCase c : upcoming) {
            String empName = resolveEmployeeName(c.getEmployeeId());
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, c.getLastWorkingDate());
            body.append("<li><strong>").append(htmlEscape(empName)).append("</strong> — LWD: ")
                    .append(c.getLastWorkingDate())
                    .append(" (").append(daysLeft == 0 ? "TODAY" : daysLeft + " day(s)").append(")")
                    .append(", case ").append(htmlEscape(c.getCaseNo()))
                    .append("</li>");
        }
        body.append("</ul>");

        email.sendSimple(hrEmail,
                "Offboarding LWD reminder — " + upcoming.size() + " employee(s)",
                body.toString());
        log.info("Sent LWD reminder digest: {} case(s) in next {} days", upcoming.size(), lwdReminderDays);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void notifyCaseStarted(UUID caseId, UUID employeeId) {
        try {
            Employee emp = employees.findById(employeeId).orElse(null);
            if (emp == null) return;
            String empName = fullName(emp);

            OffboardingCase c = caseRepo.findById(caseId).orElse(null);
            String lwdStr = (c != null && c.getLastWorkingDate() != null)
                    ? c.getLastWorkingDate().toString() : "TBD";
            String caseNo = c != null ? c.getCaseNo() : "";

            String hrBody = "<p>Offboarding process has started for <strong>" + htmlEscape(empName)
                    + "</strong> (case <strong>" + htmlEscape(caseNo) + "</strong>).</p>"
                    + "<p>Last working date: <strong>" + lwdStr + "</strong></p>"
                    + "<p>Please action any pending clearances and settlement in Millers HCM.</p>";

            if (!isBlank(hrEmail)) {
                email.sendSimple(hrEmail, "Offboarding started: " + empName, hrBody);
            }

            if (emp.getManagerId() != null) {
                employees.findById(emp.getManagerId()).ifPresent(mgr -> {
                    if (isBlank(mgr.getWorkEmail())) return;
                    String mgrBody = "<p>Hi " + htmlEscape(fullName(mgr)) + ",</p>"
                            + "<p><strong>" + htmlEscape(empName)
                            + "</strong>'s resignation / departure has been approved.</p>"
                            + "<p>Last working date: <strong>" + lwdStr + "</strong>. "
                            + "Please ensure knowledge transfer tasks are completed.</p>";
                    email.sendSimple(mgr.getWorkEmail(), "Offboarding started: " + empName, mgrBody);
                    log.info("Notified manager {} for offboarding of {}", mgr.getWorkEmail(), empName);
                });
            }
        } catch (Exception ex) {
            log.warn("Failed to send case-started notification for case {}: {}", caseId, ex.getMessage());
        }
    }

    private String resolveEmployeeName(UUID employeeId) {
        return employees.findById(employeeId).map(this::fullName).orElse(employeeId.toString());
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
