package az.millers.hcm.notifications;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.reporting.domain.ReportRun;
import az.millers.hcm.reporting.domain.ReportSchedule;
import az.millers.hcm.reporting.domain.WebhookType;

/**
 * Posts a Slack / MS Teams notification when a scheduled report
 * succeeds (PRD 10.5).
 *
 * <p>Both transports accept JSON over HTTPS via Java's built-in
 * {@link java.net.http.HttpClient}, so no extra dependency. The
 * payload schema differs per type:
 * <ul>
 *   <li><b>Slack</b> incoming webhooks expect
 *       {@code { "text": "…" }} with optional Block Kit blocks.
 *   <li><b>MS Teams</b> connectors expect the legacy MessageCard
 *       envelope ({@code "@type":"MessageCard", "summary","sections"}).
 *       Teams' newer Workflow webhooks accept Adaptive Cards; we
 *       stick with MessageCard because it works with both connectors
 *       and most channel-bridging tools.
 * </ul>
 *
 * <p>Files themselves aren't pushed through the webhook — Slack's
 * incoming-webhook surface doesn't accept uploads, and Teams' card
 * model expects external URLs. The notification carries a brief
 * summary; recipients click through to download from MinIO via the
 * normal {@code /api/attachments/{id}/download} endpoint.
 */
@Service
public class WebhookNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationService.class);

    /** Per-call timeout — keeps a misbehaving Slack edge from blocking the run. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebhookNotificationService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * @throws WebhookDeliveryException when delivery fails — the caller
     *         persists the error message on the {@code report_run}.
     */
    public void sendReportNotification(WebhookType type,
                                        String url,
                                        ReportRun run,
                                        ReportSchedule schedule) throws WebhookDeliveryException {
        if (type == null || type == WebhookType.NONE) {
            throw new WebhookDeliveryException("Webhook type is NONE — caller should have skipped");
        }
        if (url == null || url.isBlank()) {
            throw new WebhookDeliveryException("Webhook URL is blank");
        }
        String json = switch (type) {
            case SLACK -> buildSlackPayload(run, schedule);
            case TEAMS -> buildTeamsPayload(run, schedule);
            default    -> throw new WebhookDeliveryException("Unsupported webhook type: " + type);
        };
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new WebhookDeliveryException(
                        type + " webhook returned HTTP " + code + ": "
                                + truncate(response.body(), 240));
            }
            log.info("Sent {} webhook notification for run {} (HTTP {})",
                    type, run.getRunNo(), code);
        } catch (java.io.IOException ex) {
            throw new WebhookDeliveryException(
                    "I/O error posting " + type + " webhook: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WebhookDeliveryException(
                    "Interrupted posting " + type + " webhook", ex);
        } catch (IllegalArgumentException ex) {
            throw new WebhookDeliveryException(
                    "Bad webhook URL: " + ex.getMessage(), ex);
        }
    }

    // ---- payload builders -------------------------------------------------

    private String buildSlackPayload(ReportRun run, ReportSchedule schedule) throws WebhookDeliveryException {
        // Slack Block Kit — text fallback + a section with the relevant fields.
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("text", slackHeadline(run, schedule));
        payload.put("blocks", java.util.List.of(
                java.util.Map.of(
                        "type", "header",
                        "text", java.util.Map.of(
                                "type", "plain_text",
                                "text", "📊 " + (schedule != null ? schedule.getName() : run.getReportType().name()),
                                "emoji", true)),
                java.util.Map.of(
                        "type", "section",
                        "fields", java.util.List.of(
                                kv("*Run*", run.getRunNo()),
                                kv("*Report*", run.getReportType().name()),
                                kv("*Format*", run.getFormat().name()),
                                kv("*Size*", run.getSizeBytes() == null ? "?" : run.getSizeBytes() + " bytes"),
                                kv("*Finished*", String.valueOf(run.getFinishedAt())),
                                kv("*Schedule*", schedule == null ? "—" : schedule.getScheduleNo()))),
                java.util.Map.of(
                        "type", "context",
                        "elements", java.util.List.of(
                                java.util.Map.of(
                                        "type", "mrkdwn",
                                        "text", "Download from Millers HCM → Reports → Schedules → Run history. "
                                                + "Recipients managed under Reports → Schedules.")))));
        return toJson(payload);
    }

    private static java.util.Map<String, String> kv(String name, String value) {
        return java.util.Map.of("type", "mrkdwn", "text", name + "\n" + value);
    }

    private String buildTeamsPayload(ReportRun run, ReportSchedule schedule) throws WebhookDeliveryException {
        // MS Teams MessageCard (legacy O365 Connector schema). Newer Workflow
        // webhooks accept Adaptive Cards; MessageCard remains a safe default.
        java.util.Map<String, Object> facts = new java.util.LinkedHashMap<>();
        facts.put("Run", run.getRunNo());
        facts.put("Report", run.getReportType().name());
        facts.put("Format", run.getFormat().name());
        facts.put("Size",   run.getSizeBytes() == null ? "?" : run.getSizeBytes() + " bytes");
        facts.put("Finished", String.valueOf(run.getFinishedAt()));
        facts.put("Schedule", schedule == null ? "—" : schedule.getScheduleNo());

        java.util.List<java.util.Map<String, Object>> factList = facts.entrySet().stream()
                .map(e -> (java.util.Map<String, Object>)
                        java.util.Map.of("name", e.getKey(), "value", (Object) e.getValue()))
                .toList();

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("@type", "MessageCard");
        payload.put("@context", "https://schema.org/extensions");
        payload.put("summary", slackHeadline(run, schedule));
        payload.put("themeColor", "5B3FE5"); // Millers brand purple
        payload.put("title", "📊 " + (schedule != null ? schedule.getName() : run.getReportType().name()));
        payload.put("sections", java.util.List.of(
                java.util.Map.of(
                        "activityTitle", "Scheduled report ready",
                        "facts", factList,
                        "markdown", true)));
        return toJson(payload);
    }

    private static String slackHeadline(ReportRun run, ReportSchedule schedule) {
        String name = schedule != null ? schedule.getName() : run.getReportType().name();
        return "Scheduled report ready: " + name + " (" + run.getRunNo() + ", " + run.getFormat() + ")";
    }

    private String toJson(Object payload) throws WebhookDeliveryException {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new WebhookDeliveryException("Failed to serialise payload: " + ex.getMessage(), ex);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
