package az.millers.hcm.recruitment.domain;

/**
 * Candidate's standing in the talent pool (M87).
 *
 * <ul>
 *   <li>{@code ACTIVE} — open to outreach.</li>
 *   <li>{@code PASSIVE} — keep in the pool but recruiters shouldn't push hard.</li>
 *   <li>{@code ARCHIVED} — hidden from default pool searches but reachable
 *       on demand.</li>
 *   <li>{@code DO_NOT_CONTACT} — opt-out / hard suppression.</li>
 * </ul>
 */
public enum CandidatePoolStatus {
    ACTIVE,
    PASSIVE,
    ARCHIVED,
    DO_NOT_CONTACT
}
