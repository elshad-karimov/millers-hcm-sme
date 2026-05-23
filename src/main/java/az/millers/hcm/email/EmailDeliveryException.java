package az.millers.hcm.email;

/**
 * Thrown by {@link EmailService} when SMTP delivery fails. Callers should
 * catch this to surface a graceful {@code FAILED} state in their own
 * persistence model — letting it bubble would otherwise roll back the
 * surrounding transaction.
 */
public class EmailDeliveryException extends Exception {
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
