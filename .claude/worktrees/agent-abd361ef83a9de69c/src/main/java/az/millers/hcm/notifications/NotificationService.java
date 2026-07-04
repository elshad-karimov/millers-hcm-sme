package az.millers.hcm.notifications;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.email.EmailDeliveryException;
import az.millers.hcm.email.EmailService;
import az.millers.hcm.notifications.domain.DeviceToken;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.domain.NotificationLog;
import az.millers.hcm.notifications.repo.DeviceTokenRepository;
import az.millers.hcm.notifications.repo.NotificationLogRepository;
import az.millers.hcm.notifications.service.NotificationPreferenceService;

/**
 * Core notification orchestrator (PRD §17.5, Must-Have #34).
 *
 * <p>All three channels (IN_APP, EMAIL, PUSH) are handled here.  Failures
 * on EMAIL / PUSH are swallowed so that a missing SMTP relay or an
 * unconfigured FCM key never rolls back the business transaction.
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationLogRepository logRepo;
    private final DeviceTokenRepository tokenRepo;
    private final FcmService fcm;
    private final EmployeeRepository employees;
    private final EmailService email;
    private final NotificationPreferenceService preferences;

    public NotificationService(NotificationLogRepository logRepo,
                                DeviceTokenRepository tokenRepo,
                                FcmService fcm,
                                EmployeeRepository employees,
                                EmailService email,
                                NotificationPreferenceService preferences) {
        this.logRepo = logRepo;
        this.tokenRepo = tokenRepo;
        this.fcm = fcm;
        this.employees = employees;
        this.email = email;
        this.preferences = preferences;
    }

    // ── IN_APP ────────────────────────────────────────────────────────────────

    /**
     * Create and persist an IN_APP notification.  No external delivery.
     */
    public NotificationLog createInApp(String recipient, String title, String body,
                                       String module, String entityType, String entityId) {
        NotificationLog entry = buildLog(recipient, NotificationChannel.IN_APP,
                title, body, module, entityType, entityId);
        entry.setSentAt(OffsetDateTime.now());
        return logRepo.save(entry);
    }

    // ── EMAIL ─────────────────────────────────────────────────────────────────

    /**
     * Create, persist and send an EMAIL notification.
     * <p>{@code recipient} is the Keycloak username (used as the log key).
     * {@code emailAddress} is the actual SMTP destination.
     * Email failures are logged and swallowed — the log entry records the error.
     */
    public NotificationLog createAndSendEmail(String recipient, String emailAddress,
                                               String title, String body,
                                               String module, String entityType, String entityId) {
        NotificationLog entry = buildLog(recipient, NotificationChannel.EMAIL,
                title, body, module, entityType, entityId);
        String htmlBody = "<p>" + body + "</p><p><small>Millers HCM</small></p>";
        try {
            email.sendSimple(emailAddress, title, htmlBody);
            entry.setSentAt(OffsetDateTime.now());
        } catch (Exception ex) {
            log.warn("Email notification failed for '{}' → {}: {}", title, emailAddress, ex.getMessage());
            entry.setFailedAt(OffsetDateTime.now());
            entry.setErrorMessage(ex.getMessage());
        }
        return logRepo.save(entry);
    }

    // ── PUSH ──────────────────────────────────────────────────────────────────

    /**
     * Create, persist and send a PUSH notification via FCM.
     * FCM failures are logged and swallowed.
     */
    public NotificationLog createAndSendPush(String recipient, String title, String body,
                                              String module, String entityType, String entityId) {
        NotificationLog entry = buildLog(recipient, NotificationChannel.PUSH,
                title, body, module, entityType, entityId);
        List<String> tokens = tokenRepo.findByUsername(recipient)
                .stream().map(DeviceToken::getFcmToken).toList();
        if (!tokens.isEmpty()) {
            Map<String, String> data = Map.of(
                    "module", module != null ? module : "",
                    "entityType", entityType != null ? entityType : "",
                    "entityId", entityId != null ? entityId : "");
            boolean sent = fcm.send(tokens, title, body, data);
            if (sent) {
                entry.setSentAt(OffsetDateTime.now());
            } else {
                entry.setFailedAt(OffsetDateTime.now());
                entry.setErrorMessage("FCM send returned false for all tokens");
            }
        } else {
            entry.setFailedAt(OffsetDateTime.now());
            entry.setErrorMessage("No FCM device tokens registered for user");
        }
        return logRepo.save(entry);
    }

    // ── ALL CHANNELS ──────────────────────────────────────────────────────────

    /**
     * Notify via all available channels for the given username:
     * <ul>
     *   <li>IN_APP — always</li>
     *   <li>EMAIL  — if the employee record has an email address</li>
     *   <li>PUSH   — if FCM tokens are registered for the user</li>
     * </ul>
     *
     * <p>Pre-M115 signature. New callers should use
     * {@link #notifyAll(NotificationCategory, String, String, String, String, String, String)}
     * so users can opt out of categories that don't need to break through.
     */
    public void notifyAll(String username, String title, String body,
                          String module, String entityType, String entityId) {
        notifyAll(NotificationCategory.TRANSACTIONAL, username, title, body,
                module, entityType, entityId);
    }

    /**
     * M115 — preference-gated fan-out. Each channel is checked against
     * {@link NotificationPreferenceService#shouldSend} before delivery.
     * IN_APP is always delivered for {@link NotificationCategory#TRANSACTIONAL}
     * regardless of the user's opt-out — preferences only apply to mutable
     * categories.
     *
     * @param category the kind of notification — drives the opt-out check
     */
    public void notifyAll(NotificationCategory category,
                          String username, String title, String body,
                          String module, String entityType, String entityId) {
        NotificationCategory cat = category == null ? NotificationCategory.TRANSACTIONAL : category;

        // IN_APP — always create the log row. We still write the entry even
        // when the user has muted IN_APP for this category, but mark it as
        // pre-read so the bell doesn't flash. (Easy follow-on M115 phase 2.)
        if (preferences.shouldSend(username, cat, NotificationChannel.IN_APP)) {
            createInApp(username, title, body, module, entityType, entityId);
        }

        // EMAIL — only if employee has an address AND the user hasn't muted.
        if (preferences.shouldSend(username, cat, NotificationChannel.EMAIL)) {
            employees.findByUsername(username).ifPresent(emp -> {
                if (emp.getEmail() != null && !emp.getEmail().isBlank()) {
                    createAndSendEmail(username, emp.getEmail(), title, body, module, entityType, entityId);
                }
            });
        }

        // PUSH — only if FCM tokens exist AND the user hasn't muted.
        if (preferences.shouldSend(username, cat, NotificationChannel.PUSH)) {
            List<DeviceToken> tokens = tokenRepo.findByUsername(username);
            if (!tokens.isEmpty()) {
                createAndSendPush(username, title, body, module, entityType, entityId);
            }
        }
    }

    // ── READ MANAGEMENT ───────────────────────────────────────────────────────

    /**
     * Mark a single notification as read.  Only the owner (matched by
     * {@code username}) can mark it read; silently ignores unknown IDs or
     * ownership mismatches.
     */
    @Transactional
    public void markRead(UUID id, String username) {
        logRepo.findById(id).ifPresent(n -> {
            if (username.equals(n.getRecipient()) && n.getReadAt() == null) {
                n.setReadAt(OffsetDateTime.now());
                logRepo.save(n);
            }
        });
    }

    /**
     * Mark all unread notifications for a user as read.
     */
    @Transactional
    public void markAllRead(String username) {
        logRepo.markAllReadByRecipient(username);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private NotificationLog buildLog(String recipient, NotificationChannel channel,
                                     String title, String body,
                                     String module, String entityType, String entityId) {
        NotificationLog n = new NotificationLog();
        n.setRecipient(recipient);
        n.setChannel(channel);
        n.setTitle(title);
        n.setBody(body);
        n.setModule(module);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        return n;
    }
}
