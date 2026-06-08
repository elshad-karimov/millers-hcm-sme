package az.millers.hcm.engagement.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.engagement.domain.CampaignStatus;
import az.millers.hcm.engagement.domain.SurveyCampaign;
import az.millers.hcm.engagement.repo.SurveyCampaignRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;

/**
 * Daily scheduler for survey campaign lifecycle management (M186 /
 * PRD §8.16 engagement surveys).
 *
 * <p>Runs at 08:00 UTC every day:
 * <ol>
 *   <li><b>Open</b>: DRAFT campaigns with {@code opensOn ≤ today} are
 *       transitioned to ACTIVE and all active employees are sent an
 *       {@link NotificationCategory#ANNOUNCEMENT} notification.</li>
 *   <li><b>Close</b>: ACTIVE campaigns with {@code closesOn < today} are
 *       transitioned to CLOSED (no notification).</li>
 * </ol>
 *
 * <p>Notification failures for individual employees are logged and skipped
 * so a single bad recipient never blocks the activation of the campaign.
 */
@Component
public class SurveyCampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(SurveyCampaignScheduler.class);
    private static final String MODULE = "ENGAGEMENT";

    private final SurveyCampaignRepository campaigns;
    private final EmployeeRepository employees;
    private final NotificationService notifications;

    public SurveyCampaignScheduler(SurveyCampaignRepository campaigns,
                                    EmployeeRepository employees,
                                    NotificationService notifications) {
        this.campaigns = campaigns;
        this.employees = employees;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 0 8 * * *")   // 08:00 UTC daily
    @Transactional
    public void runDailyLifecycle() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        openDueCampaigns(today);
        closeDueCampaigns(today);
    }

    // ── Open ─────────────────────────────────────────────────────────────────

    private void openDueCampaigns(LocalDate today) {
        List<SurveyCampaign> due = campaigns.findDraftDueToOpen(today);
        if (due.isEmpty()) return;

        log.info("SurveyCampaignScheduler: activating {} campaign(s) on {}", due.size(), today);

        // Load active employees once for the batch (all target-all campaigns share the list).
        List<Employee> activeEmployees = null;

        for (SurveyCampaign c : due) {
            c.setStatus(CampaignStatus.ACTIVE);
            campaigns.save(c);
            log.info("SurveyCampaignScheduler: activated campaign '{}' ({})", c.getName(), c.getId());

            if (c.isTargetAll()) {
                if (activeEmployees == null) {
                    activeEmployees = employees.findAllByEmploymentStatus(EmploymentStatus.ACTIVE);
                }
                notifyEmployees(c, activeEmployees);
            }
        }
    }

    private void notifyEmployees(SurveyCampaign campaign, List<Employee> recipients) {
        String title = "New survey available: " + campaign.getName();
        String body  = "A new survey \"" + campaign.getName() + "\" is now open. "
                + "You can participate until " + campaign.getClosesOn()
                + ". Your feedback helps us improve!";

        int sent = 0;
        int failed = 0;
        for (Employee emp : recipients) {
            if (emp.getUsername() == null || emp.getUsername().isBlank()) continue;
            try {
                notifications.notifyAll(
                        NotificationCategory.ANNOUNCEMENT,
                        emp.getUsername(), title, body,
                        MODULE, "SurveyCampaign", campaign.getId().toString());
                sent++;
            } catch (Exception ex) {
                failed++;
                log.warn("SurveyCampaignScheduler: failed to notify {} for campaign {}: {}",
                        emp.getUsername(), campaign.getId(), ex.getMessage());
            }
        }
        log.info("SurveyCampaignScheduler: campaign '{}' — notified={} failed={}",
                campaign.getName(), sent, failed);
    }

    // ── Close ────────────────────────────────────────────────────────────────

    private void closeDueCampaigns(LocalDate today) {
        List<SurveyCampaign> due = campaigns.findActiveDueToClose(today);
        if (due.isEmpty()) return;

        log.info("SurveyCampaignScheduler: closing {} campaign(s) on {}", due.size(), today);
        for (SurveyCampaign c : due) {
            c.setStatus(CampaignStatus.CLOSED);
            campaigns.save(c);
            log.info("SurveyCampaignScheduler: closed campaign '{}' ({})", c.getName(), c.getId());
        }
    }
}
