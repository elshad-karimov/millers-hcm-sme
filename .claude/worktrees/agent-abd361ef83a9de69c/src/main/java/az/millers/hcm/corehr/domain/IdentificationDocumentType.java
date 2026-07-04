package az.millers.hcm.corehr.domain;

/**
 * Identification document types tracked by {@link EmployeeIdentification}
 * (M63 / P1-04).
 *
 * <p>Distinguished from {@code Employee.nationalId} which is a single
 * encrypted scalar — this table stores the supporting <em>document</em>
 * (number, issue/expiry, issuing authority). An employee may have several
 * identification documents over time (renewed passports etc.).
 */
public enum IdentificationDocumentType {

    /** Government-issued national ID card. */
    NATIONAL_ID(false),

    /** Travel passport. Has an expiry; renewals create new rows. */
    PASSPORT(true),

    /** Entry visa for a specific country. */
    VISA(true),

    /** Right-to-work authorisation; legally required for non-citizen employees. */
    WORK_PERMIT(true),

    /** Right-to-reside authorisation. */
    RESIDENCY_PERMIT(true),

    /** Driver licence — required when the job involves driving company vehicles. */
    DRIVER_LICENSE(true);

    private final boolean expirable;

    IdentificationDocumentType(boolean expirable) {
        this.expirable = expirable;
    }

    /**
     * Whether this document type typically carries a meaningful expiry date.
     * National IDs in some jurisdictions are perennial; expirable types
     * (passport, visa, work permit, etc.) feed the {@code ExpiryAlertScheduler}.
     */
    public boolean isExpirable() {
        return expirable;
    }
}
