package az.millers.hcm.reporting.service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attachment.domain.Attachment;
import az.millers.hcm.attachment.service.AttachmentService;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.email.EmailDeliveryException;
import az.millers.hcm.email.EmailService;
import az.millers.hcm.notifications.WebhookDeliveryException;
import az.millers.hcm.notifications.WebhookNotificationService;
import az.millers.hcm.reporting.domain.EmailStatus;
import az.millers.hcm.reporting.domain.WebhookStatus;
import az.millers.hcm.reporting.domain.WebhookType;
import az.millers.hcm.reporting.domain.ReportDefinition;
import az.millers.hcm.reporting.domain.ReportFormat;
import az.millers.hcm.reporting.domain.ReportRun;
import az.millers.hcm.reporting.domain.ReportRunStatus;
import az.millers.hcm.reporting.domain.ReportSchedule;
import az.millers.hcm.reporting.domain.ReportType;
import az.millers.hcm.reporting.domain.TriggerSource;
import az.millers.hcm.reporting.repo.ReportDefinitionRepository;
import az.millers.hcm.reporting.repo.ReportRunRepository;
import az.millers.hcm.reporting.repo.ReportScheduleRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Executes a report and stores the generated file via MinIO + the
 * existing {@link AttachmentService}. Each invocation writes a row in
 * {@code reporting.report_run} with status RUNNING → SUCCESS|FAILED so
 * the run history is auditable end-to-end.
 *
 * <p>Scheduled runs additionally email the rendered file to the
 * schedule's recipients (PRD 10.5). Delivery state lands on
 * {@code report_run.email_status} so the run history surfaces it
 * alongside the underlying execution status.
 */
@Service
public class ReportRunService {

    private static final Logger log = LoggerFactory.getLogger(ReportRunService.class);
    private static final String MODULE = "REPORTING";
    private static final String OWNER_MODULE = "REPORTING";
    private static final String OWNER_ENTITY = "ReportRun";

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ReportRunRepository runs;
    private final ReportDefinitionRepository definitions;
    private final ReportScheduleRepository schedules;
    private final ReportExportService exportService;
    private final AttachmentService attachmentService;
    private final EmailService emailService;
    private final WebhookNotificationService webhookService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ReportRunService(ReportRunRepository runs,
                             ReportDefinitionRepository definitions,
                             ReportScheduleRepository schedules,
                             ReportExportService exportService,
                             AttachmentService attachmentService,
                             EmailService emailService,
                             WebhookNotificationService webhookService,
                             AuditService audit,
                             CurrentRequest currentRequest) {
        this.runs = runs;
        this.definitions = definitions;
        this.schedules = schedules;
        this.exportService = exportService;
        this.attachmentService = attachmentService;
        this.emailService = emailService;
        this.webhookService = webhookService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public Page<ReportRun> list(Pageable pageable) {
        return runs.findAllByOrderByStartedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public ReportRun get(UUID id) {
        return runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report run not found: " + id));
    }

    /**
     * Synchronous run: generates the file, stores it in MinIO, and persists
     * the run + attachment id. Returns the persisted run row. If the trigger
     * is {@link TriggerSource#SCHEDULED} and the source schedule has
     * recipients, the rendered bytes are also emailed.
     */
    @Transactional
    public ReportRun runAdhoc(ReportType type, ReportFormat format,
                                Map<String, Object> params, UUID definitionId,
                                UUID scheduleId, TriggerSource trigger) {
        if (type == null) throw new BadRequestException("type is required");
        if (format == null) throw new BadRequestException("format is required");

        Map<String, Object> safeParams = params == null ? new LinkedHashMap<>() : params;
        TriggerSource src = trigger == null ? TriggerSource.MANUAL : trigger;

        ReportRun run = new ReportRun();
        run.setRunNo(String.format("RPT-%05d", runs.nextNoSequence()));
        run.setDefinitionId(definitionId);
        run.setScheduleId(scheduleId);
        run.setReportType(type);
        run.setFormat(format);
        run.setParameters(safeParams);
        run.setStatus(ReportRunStatus.RUNNING);
        run.setTriggeredBy(currentRequest.username());
        run.setTriggerSource(src);
        run.setEmailStatus(EmailStatus.NOT_REQUESTED);
        run.setWebhookStatus(WebhookStatus.NOT_REQUESTED);
        ReportRun saved = runs.save(run);

        byte[] payload;
        try {
            payload = exportService.export(type, format, safeParams);
            String filename = filename(type, format);
            Attachment att = attachmentService.uploadBytes(
                    OWNER_MODULE, OWNER_ENTITY, saved.getId(),
                    payload, filename, format.contentType());

            saved.setAttachmentId(att.getId());
            saved.setFileName(att.getOriginalFilename());
            saved.setSizeBytes(att.getSizeBytes());
            saved.setStatus(ReportRunStatus.SUCCESS);
            saved.setFinishedAt(OffsetDateTime.now());
            saved = runs.save(saved);

            audit.record(MODULE, "ReportRun", saved.getId().toString(),
                    "SUCCESS", null,
                    Map.of("runNo", saved.getRunNo(),
                            "type", type.name(),
                            "format", format.name(),
                            "attachmentNo", att.getAttachmentNo()));
        } catch (Exception e) {
            log.error("Report run {} failed", saved.getRunNo(), e);
            saved.setStatus(ReportRunStatus.FAILED);
            saved.setFinishedAt(OffsetDateTime.now());
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            saved.setErrorMessage(msg);
            runs.save(saved);
            audit.record(MODULE, "ReportRun", saved.getId().toString(),
                    "FAILED", null, Map.of("error", msg));
            throw e instanceof RuntimeException re ? re : new RuntimeException(msg, e);
        }

        // Try email + webhook delivery for scheduled runs. Failures don't
        // roll back the run — the file is in MinIO and the user can resend
        // each transport independently (PRD 10.5).
        if (src == TriggerSource.SCHEDULED && saved.getScheduleId() != null) {
            saved = deliverEmail(saved, payload);
            saved = deliverWebhook(saved);
        }
        return saved;
    }

    /** Run a saved definition manually. No email — there's no recipient list. */
    @Transactional
    public ReportRun runDefinition(UUID definitionId, ReportFormat formatOverride) {
        ReportDefinition d = definitions.findById(definitionId)
                .orElseThrow(() -> new BadRequestException("Definition not found: " + definitionId));
        ReportFormat fmt = formatOverride != null ? formatOverride : d.getDefaultFormat();
        return runAdhoc(d.getReportType(), fmt, d.getParameters(), d.getId(), null, TriggerSource.MANUAL);
    }

    /**
     * Re-attempt email delivery for an existing successful run. Fetches the
     * file from MinIO and resends through the schedule's current recipient
     * list. Used for "Resend" actions in the UI after fixing SMTP or a typo
     * in the recipient list.
     */
    @Transactional
    public ReportRun resendEmail(UUID runId) {
        ReportRun r = get(runId);
        if (r.getStatus() != ReportRunStatus.SUCCESS) {
            throw new BadRequestException("Can only resend a SUCCESS run; this one is " + r.getStatus());
        }
        if (r.getAttachmentId() == null) {
            throw new BadRequestException("Run has no stored attachment to resend");
        }
        if (r.getScheduleId() == null) {
            throw new BadRequestException("Run was not scheduled-triggered; nothing to resend to");
        }
        byte[] bytes;
        try (InputStream in = attachmentService.download(r.getAttachmentId())) {
            bytes = AttachmentService.readAll(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch attachment from MinIO: " + e.getMessage(), e);
        }
        return deliverEmail(r, bytes);
    }

    /**
     * Re-fire the Slack/Teams notification for an existing SUCCESS run.
     * Doesn't re-download the attachment — the payload only carries a
     * summary, not the file itself.
     */
    @Transactional
    public ReportRun resendWebhook(UUID runId) {
        ReportRun r = get(runId);
        if (r.getStatus() != ReportRunStatus.SUCCESS) {
            throw new BadRequestException("Can only resend a SUCCESS run; this one is " + r.getStatus());
        }
        if (r.getScheduleId() == null) {
            throw new BadRequestException("Run was not scheduled-triggered; nothing to resend to");
        }
        return deliverWebhook(r);
    }

    @Transactional(readOnly = true)
    public List<ReportRun> historyFor(UUID definitionId) {
        return runs.findByDefinitionIdOrderByStartedAtDesc(definitionId);
    }

    @Transactional(readOnly = true)
    public List<ReportRun> historyForSchedule(UUID scheduleId) {
        return runs.findByScheduleIdOrderByStartedAtDesc(scheduleId);
    }

    private ReportRun deliverEmail(ReportRun run, byte[] bytes) {
        ReportSchedule schedule = schedules.findById(run.getScheduleId()).orElse(null);
        List<String> recipients = parseRecipients(schedule == null ? null : schedule.getRecipients());
        run.setEmailRecipients(recipients.isEmpty() ? null : String.join(",", recipients));

        if (recipients.isEmpty()) {
            run.setEmailStatus(EmailStatus.SKIPPED);
            run.setEmailError("No recipients configured on schedule");
            ReportRun saved = runs.save(run);
            audit.record(MODULE, "ReportRun", saved.getId().toString(),
                    "EMAIL_SKIPPED", null,
                    Map.of("runNo", saved.getRunNo(),
                            "reason", "no_recipients"));
            return saved;
        }

        String subject = String.format("%s — %s",
                schedule != null ? schedule.getName() : "Scheduled report",
                run.getReportType().name());
        String body = buildHtmlBody(run, schedule);
        String filename = run.getFileName() != null
                ? run.getFileName()
                : filename(run.getReportType(), run.getFormat());

        try {
            emailService.sendReport(recipients, subject, body, filename, bytes, run.getFormat().contentType());
            run.setEmailStatus(EmailStatus.SENT);
            run.setEmailSentAt(OffsetDateTime.now());
            run.setEmailError(null);
            ReportRun saved = runs.save(run);
            audit.record(MODULE, "ReportRun", saved.getId().toString(),
                    "EMAIL_SENT", null,
                    Map.of("runNo", saved.getRunNo(),
                            "recipients", recipients,
                            "subject", subject));
            return saved;
        } catch (EmailDeliveryException ex) {
            run.setEmailStatus(EmailStatus.FAILED);
            run.setEmailError(ex.getMessage());
            ReportRun saved = runs.save(run);
            audit.record(MODULE, "ReportRun", saved.getId().toString(),
                    "EMAIL_FAILED", null,
                    Map.of("runNo", saved.getRunNo(),
                            "recipients", recipients,
                            "error", ex.getMessage()));
            return saved;
        }
    }

    /** Splits a comma- or semicolon-delimited recipients string. */
    static List<String> parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String buildHtmlBody(ReportRun run, ReportSchedule schedule) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Hello,</p>");
        sb.append("<p>The scheduled report <strong>")
                .append(schedule != null ? schedule.getName() : run.getReportType().name())
                .append("</strong> has run successfully and is attached as <code>")
                .append(run.getFileName())
                .append("</code>.</p>");
        sb.append("<table style=\"border-collapse:collapse;font-family:sans-serif;font-size:13px\">");
        row(sb, "Run", run.getRunNo());
        row(sb, "Report type", run.getReportType().name());
        row(sb, "Format", run.getFormat().name());
        if (schedule != null) {
            row(sb, "Schedule", schedule.getScheduleNo() + " — " + schedule.getName());
            row(sb, "Cron", schedule.getCron());
        }
        row(sb, "Finished at", String.valueOf(run.getFinishedAt()));
        row(sb, "Size", run.getSizeBytes() == null ? "?" : run.getSizeBytes() + " bytes");
        sb.append("</table>");
        sb.append("<p style=\"color:#888;font-size:12px;margin-top:24px\">"
                + "This email was sent automatically by Millers HCM. "
                + "Recipients can be edited under <em>Reports → Schedules</em>.</p>");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><td style=\"padding:4px 12px 4px 0;color:#666\">")
                .append(label)
                .append("</td><td style=\"padding:4px 0\">")
                .append(value)
                .append("</td></tr>");
    }

    private ReportRun deliverWebhook(ReportRun run) {
        ReportSchedule schedule = schedules.findById(run.getScheduleId()).orElse(null);
        WebhookType type = schedule == null ? null : schedule.getWebhookType();
        String url = schedule == null ? null : schedule.getWebhookUrl();

        if (type == null || type == WebhookType.NONE || url == null || url.isBlank()) {
            run.setWebhookStatus(WebhookStatus.SKIPPED);
            run.setWebhookTarget(null);
            run.setWebhookError(type == WebhookType.NONE || type == null
                    ? "No webhook configured on schedule"
                    : "Webhook URL missing");
            ReportRun saved = runs.save(run);
            audit.record(MODULE, "ReportRun", saved.getId().toString(),
                    "WEBHOOK_SKIPPED", null,
                    Map.of("runNo", saved.getRunNo(),
                            "reason", type == null || type == WebhookType.NONE
                                    ? "no_webhook"
                                    : "missing_url"));
            return saved;
        }

        run.setWebhookTarget(type.name() + " " + url);
        try {
            webhookService.sendReportNotification(type, url, run, schedule);
            run.setWebhookStatus(WebhookStatus.SENT);
            run.setWebhookSentAt(OffsetDateTime.now());
            run.setWebhookError(null);
            ReportRun saved = runs.save(run);
            // Audit log lives in audit.audit_log.new_value JSONB. Redact
            // the URL there — leaking the audit log shouldn't hand a Slack
            // bearer token to anyone reading it (PRD 14.3 spirit).
            audit.record(MODULE, "ReportRun", saved.getId().toString(),
                    "WEBHOOK_SENT", null,
                    Map.of("runNo", saved.getRunNo(),
                            "type", type.name(),
                            "url", maskUrl(url)));
            return saved;
        } catch (WebhookDeliveryException ex) {
            run.setWebhookStatus(WebhookStatus.FAILED);
            run.setWebhookError(ex.getMessage());
            ReportRun saved = runs.save(run);
            audit.record(MODULE, "ReportRun", saved.getId().toString(),
                    "WEBHOOK_FAILED", null,
                    Map.of("runNo", saved.getRunNo(),
                            "type", type.name(),
                            "url", maskUrl(url),
                            "error", ex.getMessage()));
            return saved;
        }
    }

    /**
     * Audit-friendly redaction: keep scheme + host (so a forensics
     * reader still recognises the channel) and replace the path
     * segments after the host with {@code …}. Returns the input as-is
     * when it can't be parsed (defence-in-depth).
     *
     * <p>Examples:
     * <pre>
     *   https://hooks.slack.com/services/T0/B0/abcd1234   →   https://hooks.slack.com/…
     *   https://millers.webhook.office.com/webhookb2/uid  →   https://millers.webhook.office.com/…
     * </pre>
     */
    static String maskUrl(String url) {
        if (url == null || url.isBlank()) return url;
        try {
            java.net.URI u = java.net.URI.create(url);
            String scheme = u.getScheme();
            String host   = u.getHost();
            if (scheme != null && host != null) {
                return scheme + "://" + host + "/…";
            }
        } catch (IllegalArgumentException ignored) { /* fall through */ }
        // Bad URL — keep just the first 20 chars so the audit row still
        // tells operators something without echoing the whole secret.
        return url.length() > 20 ? url.substring(0, 20) + "…" : url;
    }

    private String filename(ReportType type, ReportFormat format) {
        return type.name().toLowerCase() + "-"
                + OffsetDateTime.now().format(FILE_STAMP) + "." + format.extension();
    }
}
