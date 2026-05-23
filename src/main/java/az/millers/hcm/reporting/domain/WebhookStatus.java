package az.millers.hcm.reporting.domain;

/**
 * Delivery state for a scheduled report's webhook notification.
 * Mirrors {@link EmailStatus} so the two transports surface
 * identically on {@link ReportRun}.
 */
public enum WebhookStatus {
    NOT_REQUESTED,
    SKIPPED,
    SENT,
    FAILED
}
