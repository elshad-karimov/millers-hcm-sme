package az.millers.hcm.notifications.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.notifications.domain.DeliveryStatus;
import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.domain.NotificationLog;
import az.millers.hcm.notifications.domain.NotificationTemplate;
import az.millers.hcm.notifications.repo.NotificationLogRepository;
import az.millers.hcm.notifications.repo.NotificationTemplateRepository;

/**
 * M493 — Notification template + delivery log service. Templates are a seam —
 * existing hardcoded texts stay; render() used where a template code matches.
 */
@Service
@Transactional
public class NotificationTemplateService {

    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateService.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final NotificationTemplateRepository templateRepo;
    private final NotificationLogRepository logRepo;
    private final AuditService audit;

    public NotificationTemplateService(NotificationTemplateRepository templateRepo,
                                        NotificationLogRepository logRepo,
                                        AuditService audit) {
        this.templateRepo = templateRepo;
        this.logRepo = logRepo;
        this.audit = audit;
    }

    // ── Template CRUD ──────────────────────────────────────────────────────────

    public NotificationTemplate create(String code, String name, NotificationChannel channel,
                                        String subjectTemplate, String bodyTemplate,
                                        String createdBy) {
        templateRepo.findByCodeAndTenantId(code, TenantContext.current()).ifPresent(existing -> {
            throw new IllegalArgumentException("Template code already exists: " + code);
        });

        NotificationTemplate template = new NotificationTemplate();
        template.setTenantId(TenantContext.current());
        template.setCode(code);
        template.setName(name);
        template.setChannel(channel);
        template.setSubjectTemplate(subjectTemplate);
        template.setBodyTemplate(bodyTemplate);
        template.setCreatedBy(createdBy);

        NotificationTemplate saved = templateRepo.save(template);

        audit.record("NOTIFICATION", "NotificationTemplate", saved.getId().toString(),
                    "CREATED", null, saved);

        log.info("Created notification template {} ({})", code, name);
        return saved;
    }

    public NotificationTemplate update(UUID id, String name, NotificationChannel channel,
                                        String subjectTemplate, String bodyTemplate,
                                        boolean active, String updatedBy) {
        NotificationTemplate template = templateRepo.findByIdAndTenantId(id, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));

        NotificationTemplate before = clone(template);

        template.setName(name);
        template.setChannel(channel);
        template.setSubjectTemplate(subjectTemplate);
        template.setBodyTemplate(bodyTemplate);
        template.setActive(active);
        template.setUpdatedBy(updatedBy);

        NotificationTemplate saved = templateRepo.save(template);

        audit.record("NOTIFICATION", "NotificationTemplate", saved.getId().toString(),
                    "UPDATED", before, saved);

        log.info("Updated notification template {}", template.getCode());
        return saved;
    }

    public void delete(UUID id, String deletedBy) {
        NotificationTemplate template = templateRepo.findByIdAndTenantId(id, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));

        templateRepo.delete(template);

        audit.record("NOTIFICATION", "NotificationTemplate", id.toString(),
                    "DELETED", template, null);

        log.info("Deleted notification template {} by {}", template.getCode(), deletedBy);
    }

    public List<NotificationTemplate> listAll() {
        return templateRepo.findByTenantIdOrderByNameAsc(TenantContext.current());
    }

    public NotificationTemplate get(UUID id) {
        return templateRepo.findByIdAndTenantId(id, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));
    }

    // ── Template rendering seam ────────────────────────────────────────────────

    /**
     * Render a template with variable substitution. Returns Optional.empty() if
     * template code doesn't exist — caller should fall back to hardcoded text.
     */
    public Optional<RenderedTemplate> render(String code, Map<String, String> variables) {
        Optional<NotificationTemplate> templateOpt = templateRepo.findByCodeAndTenantId(code, TenantContext.current());
        if (templateOpt.isEmpty() || !templateOpt.get().isActive()) {
            return Optional.empty();
        }

        NotificationTemplate template = templateOpt.get();
        String subject = substitute(template.getSubjectTemplate(), variables);
        String body = substitute(template.getBodyTemplate(), variables);

        return Optional.of(new RenderedTemplate(template.getChannel(), subject, body));
    }

    private String substitute(String text, Map<String, String> variables) {
        if (text == null) return null;
        Matcher matcher = VAR_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, "{{" + varName + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // ── Delivery log (canonical notification.notification_log) ──────────────────

    /**
     * Record a delivery-audit entry on the canonical {@code notification_log}.
     * Status is captured the same way {@code NotificationService} does it:
     * SENT sets {@code sent_at}; FAILED sets {@code failed_at} + the error.
     * The audit {@code subject} maps onto {@code title} (the canonical
     * subject/heading field) and {@code sourceModule} onto {@code module}.
     * Non-fatal — swallows exceptions so logging failures don't break the caller.
     */
    public void recordDelivery(NotificationChannel channel, String recipient, String subject,
                                DeliveryStatus status, String errorMessage, String sourceModule) {
        try {
            NotificationLog entry = new NotificationLog();
            entry.setRecipient(recipient);
            entry.setChannel(channel);
            // title is NOT NULL — fall back to a synthesized label for channels
            // (IN_APP/PUSH) that carry no subject.
            entry.setTitle(subject != null && !subject.isBlank()
                ? subject
                : (sourceModule != null ? sourceModule : channel.name()) + " notification");
            entry.setBody("");            // audit row carries no body (body is NOT NULL)
            entry.setModule(sourceModule);
            if (status == DeliveryStatus.FAILED) {
                entry.setFailedAt(OffsetDateTime.now());
                entry.setErrorMessage(errorMessage);
            } else {
                entry.setSentAt(OffsetDateTime.now());
            }
            logRepo.save(entry);
        } catch (Exception ex) {
            log.error("Failed to record delivery log: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Delivery-audit view over the canonical log, with the same filters the old
     * delivery_log endpoint exposed. Status is derived from the timestamps.
     */
    public List<DeliveryLogView> getDeliveryLogs(NotificationChannel channel, DeliveryStatus status,
                                                 OffsetDateTime from, OffsetDateTime to, int limit) {
        Pageable page = PageRequest.of(0, limit);
        boolean sentOnly = status == DeliveryStatus.SENT;
        boolean failedOnly = status == DeliveryStatus.FAILED;
        return logRepo.findDeliveries(channel, sentOnly, failedOnly, from, to, page).stream()
            .map(DeliveryLogView::of)
            .toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private NotificationTemplate clone(NotificationTemplate t) {
        NotificationTemplate copy = new NotificationTemplate();
        copy.setId(t.getId());
        copy.setCode(t.getCode());
        copy.setName(t.getName());
        copy.setChannel(t.getChannel());
        copy.setSubjectTemplate(t.getSubjectTemplate());
        copy.setBodyTemplate(t.getBodyTemplate());
        copy.setActive(t.isActive());
        return copy;
    }

    // ── DTOs ───────────────────────────────────────────────────────────────────

    public record RenderedTemplate(NotificationChannel channel, String subject, String body) {}

    /**
     * Read-projection of a {@code notification_log} row as a delivery-audit
     * record. {@code subject} = the log title; {@code status} is derived from
     * the timestamps; {@code sentAt} falls back to failed_at then created_at.
     */
    public record DeliveryLogView(UUID id, NotificationChannel channel, String recipient,
                                  String subject, DeliveryStatus status, String errorMessage,
                                  OffsetDateTime sentAt) {

        static DeliveryLogView of(NotificationLog n) {
            DeliveryStatus status = n.getFailedAt() != null ? DeliveryStatus.FAILED : DeliveryStatus.SENT;
            OffsetDateTime when = n.getSentAt() != null ? n.getSentAt()
                : (n.getFailedAt() != null ? n.getFailedAt() : n.getCreatedAt());
            return new DeliveryLogView(n.getId(), n.getChannel(), n.getRecipient(),
                n.getTitle(), status, n.getErrorMessage(), when);
        }
    }
}
