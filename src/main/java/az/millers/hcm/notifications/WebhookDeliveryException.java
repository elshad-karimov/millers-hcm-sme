package az.millers.hcm.notifications;

/**
 * Thrown by {@link WebhookNotificationService} when an outbound POST
 * fails (HTTP non-2xx, IO error, timeout, bad URL). Callers catch this
 * to surface a graceful {@code FAILED} delivery state on
 * {@code report_run} without rolling back the surrounding transaction.
 */
public class WebhookDeliveryException extends Exception {
    public WebhookDeliveryException(String message) {
        super(message);
    }
    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
