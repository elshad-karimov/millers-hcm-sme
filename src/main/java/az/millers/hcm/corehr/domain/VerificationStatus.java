package az.millers.hcm.corehr.domain;

/**
 * Document verification state for {@link EmployeeIdentification} and (in future
 * milestones) {@code EmployeeCertification} and education credentials.
 *
 * <p>Kept as a separate enum so any document-style entity can adopt it without
 * introducing yet another bespoke status enum.
 */
public enum VerificationStatus {
    /** Newly recorded — HR has not yet reviewed the supporting attachment. */
    UNVERIFIED,
    /** HR has reviewed the attachment and confirmed the data matches. */
    VERIFIED,
    /** HR has reviewed the attachment and the data does NOT match. */
    REJECTED
}
