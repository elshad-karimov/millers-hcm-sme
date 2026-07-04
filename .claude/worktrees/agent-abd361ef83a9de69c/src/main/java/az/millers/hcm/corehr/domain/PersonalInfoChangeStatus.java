package az.millers.hcm.corehr.domain;

/**
 * Lifecycle for {@link PersonalInfoChangeRequest} (M79 / P2-25).
 *
 * <pre>
 *   PENDING ──approve──► APPROVED ──apply──► APPLIED
 *      │
 *      └─reject──► REJECTED   (terminal)
 *      └─cancel──► CANCELLED  (terminal)
 * </pre>
 */
public enum PersonalInfoChangeStatus {
    PENDING,
    APPROVED,
    REJECTED,
    APPLIED,
    CANCELLED
}
