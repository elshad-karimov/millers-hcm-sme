package az.millers.hcm.common.expiry;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Marker for any entity that carries an expiry date and should fire reminder
 * alerts as that date approaches (M61).
 *
 * <p>Implementations include — and will include, as Phase 1 progresses:
 * <ul>
 *   <li>{@code EmployeeIdentification} (passport, visa, work permit, residency,
 *       driver license)</li>
 *   <li>{@code EmploymentContract} (fixed-term contracts; probation period)</li>
 *   <li>{@code EmployeeCertification} (external professional licences)</li>
 *   <li>{@code EmployeeHealth} (fitness certificates, periodic health checks)</li>
 * </ul>
 *
 * <p>The single {@link ExpiryAlertScheduler} consumes every {@link ExpiryAlertSource}
 * Spring-registered bean and routes alerts through {@code NotificationService}.
 * Adding a new expiry-bearing entity therefore requires:
 * <ol>
 *   <li>Have the entity implement this interface, and</li>
 *   <li>Publish an {@link ExpiryAlertSource} Spring bean that knows how to query it.</li>
 * </ol>
 * No new scheduler code, no duplicated date arithmetic, no duplicated
 * notification plumbing.
 */
public interface ExpiryTrackable {

    /** Surrogate primary key of the expiry-bearing row. */
    UUID getId();

    /** Employee the document belongs to — used as the notification recipient lookup key. */
    UUID getEmployeeId();

    /** Date on which this document becomes invalid. Never {@code null}. */
    LocalDate getExpiryDate();

    /**
     * Display label for the entity kind, e.g. "Passport", "Work Permit", "PMP
     * Certification". Used as part of the notification title.
     */
    String getEntityLabel();

    /**
     * Free-text human-readable identifier for the specific document, e.g.
     * "AZ-PASSPORT-1234567" or "Visa B1/B2". Used as part of the notification body.
     */
    String getDisplayName();
}
