package az.millers.hcm.corehr.domain;

/**
 * Logical address slot for {@link EmployeeAddress} (M63 / P1-07).
 *
 * <p>An employee may have one open ({@code effective_to IS NULL}) address per
 * type — enforced by a partial unique index in V51. Past addresses are kept
 * for tax / statutory residency reconstruction.
 */
public enum AddressType {
    /** Where the employee actually lives — used for tax jurisdiction. */
    HOME,
    /** Where official mail should be sent (often == HOME, but not always). */
    MAILING,
    /** Office or duty-station address. */
    WORK,
    /** Address to contact in emergencies (next-of-kin's residence). */
    EMERGENCY
}
