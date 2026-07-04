package az.millers.hcm.preboarding.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.preboarding.domain.PreboardingInvite;
import az.millers.hcm.preboarding.domain.PreboardingStatus;
import az.millers.hcm.preboarding.repo.PreboardingInviteRepository;

/**
 * Nightly scheduler that expires pre-boarding invites that have passed their
 * deadline without being submitted (M190 / PRD §8.12 Pre-boarding).
 *
 * <p>Runs at 02:00 UTC daily. Finds every invite in SENT or OPENED status
 * whose {@code expiresAt} is before the current moment, transitions it to
 * EXPIRED, and notifies the HR issuer ({@code createdBy}) so they can
 * reissue or follow up with the candidate.
 *
 * <p>Notification failures are caught per-invite and never abort the batch.
 */
@Component
public class PreboardingExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PreboardingExpiryScheduler.class);

    private static final List<PreboardingStatus> EXPIRABLE =
            List.of(PreboardingStatus.SENT, PreboardingStatus.OPENED);

    private final PreboardingInviteRepository repo;
    private final EmployeeRepository employees;
    private final NotificationService notifications;

    public PreboardingExpiryScheduler(PreboardingInviteRepository repo,
                                       EmployeeRepository employees,
                                       NotificationService notifications) {
        this.repo = repo;
        this.employees = employees;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void expireStaleInvites() {
        OffsetDateTime now = OffsetDateTime.now();
        List<PreboardingInvite> stale = repo.findByStatusInAndExpiresAtBefore(EXPIRABLE, now);
        if (stale.isEmpty()) return;

        log.info("PreboardingExpiryScheduler: expiring {} stale invite(s)", stale.size());
        int expired = 0;
        for (PreboardingInvite invite : stale) {
            invite.setStatus(PreboardingStatus.EXPIRED);
            repo.save(invite);
            expired++;

            // Notify the HR user who issued the invite.
            if (invite.getCreatedBy() != null && !invite.getCreatedBy().isBlank()) {
                Optional<Employee> emp = employees.findById(invite.getEmployeeId());
                String empName = emp.map(e -> e.getFirstName() + " " + e.getLastName())
                                    .orElse("(unknown employee)");
                String title = "Pre-boarding invite expired: " + empName;
                String body = "The pre-boarding invite for " + empName
                        + " expired on " + invite.getExpiresAt().toLocalDate()
                        + " without being submitted. Reissue the invite if needed.";
                try {
                    notifications.notifyAll(
                            NotificationCategory.PREBOARDING,
                            invite.getCreatedBy(), title, body,
                            "PREBOARDING", "PreboardingInvite", invite.getId().toString());
                } catch (Exception ex) {
                    log.warn("PreboardingExpiryScheduler: failed to notify {} for invite {}: {}",
                            invite.getCreatedBy(), invite.getId(), ex.getMessage());
                }
            }
        }
        log.info("PreboardingExpiryScheduler: marked {} invite(s) as EXPIRED", expired);
    }
}
