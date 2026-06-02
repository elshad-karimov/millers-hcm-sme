package az.millers.hcm.common.expiry;

import java.time.LocalDate;
import java.util.List;

/**
 * Registry-style plug-in for the {@link ExpiryAlertScheduler}.
 *
 * <p>Each Phase-1 entity that participates in expiry alerts (identification,
 * contract, certification, health record) publishes one Spring bean
 * implementing this interface. The scheduler auto-injects {@code List<ExpiryAlertSource>}
 * and walks each source for each alert window — so the alert wiring is open
 * for extension and closed for modification: new sources just appear in the
 * list at startup with no scheduler changes.
 *
 * <p>Implementations are typically thin facades over a JPA repository method
 * such as {@code findByExpiryDate(date)}, and must be read-only / idempotent —
 * the scheduler may call them many times per day.
 */
public interface ExpiryAlertSource {

    /**
     * The owning {@code ownerModule} for cross-referencing the alert event
     * back into the notification + audit subsystems, e.g. {@code "PERSONNEL"}
     * or {@code "LIFECYCLE"}.
     */
    String moduleName();

    /**
     * The owning {@code ownerEntity} for cross-referencing, e.g.
     * {@code "EmployeeIdentification"} or {@code "EmploymentContract"}.
     * Matches the value the entity uses with {@code AttachmentService.upload}.
     */
    String entityName();

    /**
     * All rows whose {@link ExpiryTrackable#getExpiryDate()} equals exactly the
     * given calendar day. The scheduler invokes this once per (alert-window, source)
     * pair daily, so implementations should hit a covering index.
     *
     * @param date the calendar day to match — equality, not range
     * @return zero or more expiry-bearing entities; never {@code null}
     */
    List<? extends ExpiryTrackable> findExpiringOn(LocalDate date);
}
