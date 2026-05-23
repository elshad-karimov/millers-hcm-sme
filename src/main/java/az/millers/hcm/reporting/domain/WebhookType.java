package az.millers.hcm.reporting.domain;

/**
 * Webhook flavour for scheduled-report notifications (PRD 10.5).
 *
 * <ul>
 *   <li>{@link #NONE} — schedule has no webhook target.
 *   <li>{@link #SLACK} — Slack-incoming-webhook payload shape:
 *       {@code { "text": "…", "blocks": [ … ] }}.
 *   <li>{@link #TEAMS} — Microsoft Teams legacy Office 365 Connector
 *       payload shape (MessageCard schema).
 * </ul>
 */
public enum WebhookType {
    NONE,
    SLACK,
    TEAMS
}
