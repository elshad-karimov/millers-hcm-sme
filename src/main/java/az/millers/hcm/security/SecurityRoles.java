package az.millers.hcm.security;

/**
 * Single source of truth for HCM role names and the role-set SpEL strings
 * passed to {@code @PreAuthorize} (M90).
 *
 * <p><strong>Why this class exists:</strong> as of M89 the codebase had 414
 * {@code @PreAuthorize} annotations and 75+ inline {@code hasAnyRole(...)}
 * calls. Around two dozen controllers had factored a local
 * {@code private static final String READ_ROLES} / {@code WRITE_ROLES}, but
 * each was redefined per-controller, so the role tuples themselves were
 * still duplicated. Adding a new role (or removing an old one) required a
 * grep-and-pray across the whole module tree.
 *
 * <p><strong>Convention:</strong>
 * <ul>
 *   <li>{@code R_*} constants are bare role names (without the
 *       {@code ROLE_} prefix Spring Security adds).</li>
 *   <li>{@code Read.*} / {@code Write.*} constants are fully-formed SpEL
 *       expressions ready to pass into {@code @PreAuthorize}.</li>
 *   <li>The role-set constants are grouped by <em>capability</em>, not by
 *       module — a capability like {@code Read.HR} answers "who can read
 *       HR-owned employee data" and is reused everywhere that question is
 *       asked.</li>
 * </ul>
 *
 * <p>Adding a new role-set: add the SpEL string here. Adding a new role to
 * an existing set: edit the one constant. Both changes ripple to every
 * controller automatically — that is the entire point.
 *
 * <p>Class-level SpEL must be a compile-time-constant {@code String}, so
 * everything here is a {@code public static final String}, not a method.
 */
public final class SecurityRoles {

    private SecurityRoles() {}

    // ── Role names ──────────────────────────────────────────────────────────

    public static final String R_SYSTEM_ADMIN        = "SYSTEM_ADMIN";
    public static final String R_HR_ADMIN            = "HR_ADMIN";
    public static final String R_HR_SPECIALIST       = "HR_SPECIALIST";
    public static final String R_AUDITOR             = "AUDITOR";
    public static final String R_RECRUITER           = "RECRUITER";
    public static final String R_DEPARTMENT_MANAGER  = "DEPARTMENT_MANAGER";
    public static final String R_EMPLOYEE            = "EMPLOYEE";
    public static final String R_OCCUPATIONAL_HEALTH = "OCCUPATIONAL_HEALTH";
    public static final String R_PAYROLL_MANAGER     = "PAYROLL_MANAGER";

    // ── Read role-sets ──────────────────────────────────────────────────────

    /** Recruitment read surface — vacancies, candidates, applications, interviews, analytics. */
    public static final String READ_RECRUITMENT =
            "hasAnyRole('" + R_SYSTEM_ADMIN + "','" + R_HR_ADMIN + "','"
                    + R_HR_SPECIALIST + "','" + R_RECRUITER + "','" + R_AUDITOR + "')";

    /** Recruitment write surface — manage candidates, vacancies, interview kits, talent pool. */
    public static final String WRITE_RECRUITMENT =
            "hasAnyRole('" + R_SYSTEM_ADMIN + "','" + R_HR_ADMIN + "','" + R_RECRUITER + "')";

    /**
     * Recruitment read surface that ALSO grants department managers — used
     * for the interview-rounds surface (technical interviewers are usually
     * the hiring manager, not the recruiter).
     */
    public static final String READ_INTERVIEWS =
            "hasAnyRole('" + R_SYSTEM_ADMIN + "','" + R_HR_ADMIN + "','"
                    + R_HR_SPECIALIST + "','" + R_RECRUITER + "','" + R_AUDITOR + "','"
                    + R_DEPARTMENT_MANAGER + "')";

    /** Same as {@link #READ_INTERVIEWS} but excludes AUDITOR (writes). */
    public static final String WRITE_INTERVIEWS =
            "hasAnyRole('" + R_SYSTEM_ADMIN + "','" + R_HR_ADMIN + "','"
                    + R_HR_SPECIALIST + "','" + R_RECRUITER + "','" + R_DEPARTMENT_MANAGER + "')";

    /** Core HR read surface — employee directory, org units, position assignments. */
    public static final String READ_HR =
            "hasAnyRole('" + R_SYSTEM_ADMIN + "','" + R_HR_ADMIN + "','"
                    + R_HR_SPECIALIST + "','" + R_AUDITOR + "')";

    /** Core HR write surface — edit employee, terminate, re-org. */
    public static final String WRITE_HR =
            "hasAnyRole('" + R_HR_ADMIN + "','" + R_HR_SPECIALIST + "')";

    /** Lifecycle read surface — termination + contract change + probation + disciplinary. */
    public static final String READ_LIFECYCLE =
            "hasAnyRole('" + R_SYSTEM_ADMIN + "','" + R_HR_ADMIN + "','"
                    + R_HR_SPECIALIST + "','" + R_AUDITOR + "','" + R_DEPARTMENT_MANAGER + "')";

    /** Lifecycle write surface. */
    public static final String WRITE_LIFECYCLE =
            "hasAnyRole('" + R_HR_ADMIN + "','" + R_HR_SPECIALIST + "')";

    /** Reporting + analytics read surface. */
    public static final String READ_REPORTS =
            "hasAnyRole('" + R_SYSTEM_ADMIN + "','" + R_HR_ADMIN + "','"
                    + R_HR_SPECIALIST + "','" + R_AUDITOR + "','" + R_DEPARTMENT_MANAGER + "')";
}
