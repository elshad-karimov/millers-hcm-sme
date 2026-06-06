package az.millers.hcm.skills.domain;

/**
 * M127 — how serious a skill gap is.
 *
 * <ul>
 *   <li>{@link #NONE} — employee meets or exceeds the requirement,</li>
 *   <li>{@link #MINOR} — under by 1 level, or any level under for an
 *       optional competency,</li>
 *   <li>{@link #MAJOR} — under by 2 levels for any competency, or
 *       under by 1 for a mandatory competency,</li>
 *   <li>{@link #BLOCKER} — under by 2+ levels for a mandatory
 *       competency, or completely missing a mandatory competency.</li>
 * </ul>
 */
public enum GapSeverity {
    NONE,
    MINOR,
    MAJOR,
    BLOCKER
}
