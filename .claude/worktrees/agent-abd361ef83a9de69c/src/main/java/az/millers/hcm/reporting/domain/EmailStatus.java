package az.millers.hcm.reporting.domain;

/**
 * Delivery state for the generated report file attached to a {@link ReportRun}.
 *
 * <ul>
 *   <li>{@link #NOT_REQUESTED} — manual/ad-hoc run; email was never requested.
 *       Default value for legacy rows + the sync /export endpoint.
 *   <li>{@link #SKIPPED} — email was requested but the schedule had no
 *       recipients configured. Still a "successful" run.
 *   <li>{@link #SENT} — at least one recipient accepted the message.
 *   <li>{@link #FAILED} — the SMTP relay rejected delivery, or unreachable.
 *       Inspect {@code email_error} for the underlying message.
 * </ul>
 */
public enum EmailStatus {
    NOT_REQUESTED,
    SKIPPED,
    SENT,
    FAILED
}
