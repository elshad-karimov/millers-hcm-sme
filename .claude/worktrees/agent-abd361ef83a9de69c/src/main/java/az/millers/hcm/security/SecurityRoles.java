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
 *   <li>{@code READ_*} / {@code WRITE_*} constants are fully-formed SpEL
 *       expressions ready to pass into {@code @PreAuthorize}.</li>
 *   <li>Role-sets are named by <em>capability</em>, not module — a single
 *       capability like {@code READ_HR_PLUS_MANAGERS} answers "which roles
 *       can read HR-owned data including the employee's direct line
 *       manager" and is reused everywhere that question is asked. Sharing
 *       happens because the capability is the same, not because the names
 *       happen to overlap.</li>
 * </ul>
 *
 * <p>Class-level SpEL must be a compile-time-constant {@code String}, so
 * everything here is {@code public static final String}, not a method.
 *
 * <h3>Adding a new capability</h3>
 * <p>Add a new {@code READ_X} / {@code WRITE_X} constant. Adding a role to
 * an existing capability: edit the one constant. Both changes ripple to
 * every controller automatically — that is the entire point.
 */
public final class SecurityRoles {

    private SecurityRoles() {}

    // ── Role names ──────────────────────────────────────────────────────────
    // (verified against codebase role usage as of M90 — keep this list in
    // sync with the Keycloak realm definition)

    public static final String R_SYSTEM_ADMIN        = "SYSTEM_ADMIN";
    public static final String R_HR_ADMIN            = "HR_ADMIN";
    public static final String R_HR_SPECIALIST      = "HR_SPECIALIST";
    public static final String R_AUDITOR             = "AUDITOR";
    public static final String R_RECRUITER           = "RECRUITER";
    public static final String R_DEPARTMENT_MANAGER  = "DEPARTMENT_MANAGER";
    public static final String R_EMPLOYEE            = "EMPLOYEE";
    public static final String R_OCCUPATIONAL_HEALTH = "OCCUPATIONAL_HEALTH";
    public static final String R_PAYROLL_SPECIALIST  = "PAYROLL_SPECIALIST";
    public static final String R_FINANCE_USER        = "FINANCE_USER";

    // ── Building blocks ─────────────────────────────────────────────────────
    // Small primitives that the named role-sets below compose. Kept package-
    // private so callers compose via the named role-sets, not these.

    private static final String SA      = "'" + R_SYSTEM_ADMIN + "'";
    private static final String HRA     = "'" + R_HR_ADMIN + "'";
    private static final String HRS     = "'" + R_HR_SPECIALIST + "'";
    private static final String DM      = "'" + R_DEPARTMENT_MANAGER + "'";
    private static final String AUD     = "'" + R_AUDITOR + "'";
    private static final String REC     = "'" + R_RECRUITER + "'";
    private static final String PAYR    = "'" + R_PAYROLL_SPECIALIST + "'";
    private static final String FIN     = "'" + R_FINANCE_USER + "'";

    // ── HR (employee directory, profile, lifecycle) ─────────────────────────

    /** HR-only read: directory, leave admin, sensitive HR documents. */
    public static final String READ_HR =
            "hasAnyRole(" + SA + "," + HRA + "," + HRS + "," + AUD + ")";

    /**
     * HR read + direct-line department manager. Used by profile tabs,
     * assets/notes/rewards, assignment history, status overlays, employment
     * contracts, probation reviews, org-unit policies, employee-mgmt reports.
     */
    public static final String READ_HR_PLUS_MANAGERS =
            "hasAnyRole(" + SA + "," + HRA + "," + HRS + "," + DM + "," + AUD + ")";

    /** HR write — most "edit employee X" endpoints. */
    public static final String WRITE_HR =
            "hasAnyRole(" + SA + "," + HRA + "," + HRS + ")";

    /** HR admin-only write — narrower; e.g. leave-group master, bank-account assignment. */
    public static final String WRITE_HR_ADMIN_ONLY =
            "hasAnyRole(" + SA + "," + HRA + ")";

    /** HR write + department managers — e.g. probation review submission. */
    public static final String WRITE_HR_PLUS_MANAGERS =
            "hasAnyRole(" + SA + "," + HRA + "," + HRS + "," + DM + ")";

    // ── Recruitment ─────────────────────────────────────────────────────────

    /** Recruitment read — vacancies, candidates, applications, interview kits, analytics. */
    public static final String READ_RECRUITMENT =
            "hasAnyRole(" + SA + "," + HRA + "," + HRS + "," + REC + "," + AUD + ")";

    /** Recruitment write — manage candidates, kits, talent pool. */
    public static final String WRITE_RECRUITMENT =
            "hasAnyRole(" + SA + "," + HRA + "," + REC + ")";

    /** Interview rounds — adds DEPARTMENT_MANAGER (the hiring manager). */
    public static final String READ_INTERVIEWS =
            "hasAnyRole(" + SA + "," + HRA + "," + HRS + "," + REC + "," + AUD + "," + DM + ")";

    /** Interview rounds — write surface (schedule, score, decision). */
    public static final String WRITE_INTERVIEWS =
            "hasAnyRole(" + SA + "," + HRA + "," + HRS + "," + REC + "," + DM + ")";

    // ── Payroll ─────────────────────────────────────────────────────────────

    /** Payroll read — runs, results, payslips. Includes FINANCE_USER for treasury view. */
    public static final String READ_PAYROLL =
            "hasAnyRole(" + SA + "," + HRA + "," + HRS + "," + PAYR + "," + AUD + "," + FIN + ")";

    /** Payroll read — internal HR view (bank accounts, encrypted PII). No FINANCE_USER. */
    public static final String READ_PAYROLL_INTERNAL =
            "hasAnyRole(" + SA + "," + HRA + "," + HRS + "," + PAYR + "," + AUD + ")";

    /** Payroll calculation / approval write. */
    public static final String WRITE_PAYROLL =
            "hasAnyRole(" + SA + "," + HRA + "," + PAYR + ")";

    // ── Reporting ───────────────────────────────────────────────────────────

    /** Reporting + analytics — same shape as {@link #READ_HR_PLUS_MANAGERS}. */
    public static final String READ_REPORTS = READ_HR_PLUS_MANAGERS;

    // ── Audit ──────────────────────────────────────────────────────────────

    /**
     * Audit-log browser (M114). Deliberately narrower than {@link #READ_HR} —
     * HR_SPECIALIST is excluded so an actor can't browse their own log entries
     * to discover what HR_ADMIN saw of their actions. AUDITOR exists for this
     * exact purpose.
     */
    public static final String READ_AUDIT =
            "hasAnyRole(" + SA + "," + HRA + "," + AUD + ")";
}
