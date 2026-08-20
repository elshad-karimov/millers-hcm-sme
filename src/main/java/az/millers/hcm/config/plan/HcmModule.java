package az.millers.hcm.config.plan;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The product's navigable modules — the server-side twin of the frontend
 * {@code CATEGORIES} list in {@code web/src/nav/modules.tsx}.
 *
 * <p>Each constant carries the {@code /api} path prefixes it owns, which is what
 * turns "this tenant's plan excludes recruitment" into a real 403 instead of a
 * hidden nav tile ({@link ModuleAccessFilter}). Keys MUST stay byte-identical to
 * the frontend category keys — they are the wire format of the
 * {@code disabled_modules} tenant setting and of {@code /api/module-settings}.
 *
 * <h2>Deliberately unmapped paths</h2>
 * A path that matches no module is <em>shared</em> and never gated: reference
 * data ({@code /api/holidays}, {@code /api/grades}, {@code /api/job-functions}),
 * cross-cutting infrastructure ({@code /api/attachments}, {@code /api/documents},
 * {@code /api/notifications}), and the auth/config surface. Those are read by
 * forms across every module, so binding them to one module would break a lean
 * plan's core screens. Module gating is commercial packaging, not a security
 * boundary — under-gating is the safe direction, and role/tenant/hierarchy
 * checks are untouched either way.
 */
public enum HcmModule {

    /* ---- always-on: a tenant on any plan keeps these ---------------------- */

    /** Own profile, payslips, own requests. Never gated — an employee's own data. */
    SELF_SERVICE("self-service", "Self-Service", true, "/api/self", "/api/me"),

    /** The admin surface that re-enables everything else. Never gated. */
    PLATFORM_ADMIN("platform-admin", "Platform & Admin", true,
            "/api/admin", "/api/api-keys", "/api/audit", "/api/settings",
            "/api/module-settings", "/api/config", "/api/security", "/api/bi"),

    /* ---- gateable modules ------------------------------------------------- */

    MANAGER_SELF_SERVICE("manager-self-service", "Manager Self-Service", false,
            "/api/manager", "/api/presence"),

    CORE_HR_EMPLOYEE_MANAGEMENT("core-hr-employee-management", "Employee Management", false,
            "/api/employees", "/api/personal-info-changes", "/api/reports/emp-mgmt"),

    CORE_HR_ORGANIZATION("core-hr-organization", "Organization", false,
            "/api/org", "/api/org-unit-types", "/api/locations", "/api/legal-entities",
            "/api/hr-partners", "/api/reports/org", "/api/reports/span-of-control"),

    CORE_HR_STAFFING_POSITIONS("core-hr-staffing-positions", "Staffing & Positions", false,
            "/api/positions", "/api/position-occupancies", "/api/position-profile-grants",
            "/api/position-replacements", "/api/staffing", "/api/staffing-tables",
            "/api/workforce-plans"),

    CORE_HR_HR_OPERATIONS("core-hr-hr-operations", "HR Operations", false,
            "/api/assets", "/api/helpdesk", "/api/hr", "/api/announcements",
            "/api/letter-requests", "/api/letter-templates", "/api/policies",
            "/api/preboarding"),

    EMPLOYEE_LIFECYCLE("employee-lifecycle", "Employee Lifecycle", false,
            "/api/lifecycle", "/api/onboarding", "/api/contingent", "/api/mobility",
            "/api/checklists", "/api/reports/contract-changes"),

    EMPLOYEE_RELATIONS("employee-relations", "Employee Relations", false,
            "/api/er", "/api/disciplinary"),

    TIME_ATTENDANCE("time-attendance", "Time & Attendance", false,
            "/api/attendance", "/api/timesheets", "/api/timesheet", "/api/reports/attendance"),

    LEAVE_ABSENCE("leave-absence", "Leave & Absence", false,
            "/api/leave", "/api/permission"),

    TRAVEL_EXPENSE("travel-expense", "Travel & Expense", false,
            "/api/business-trips", "/api/expense-claims"),

    PAYROLL("payroll", "Payroll", false,
            "/api/payroll", "/api/payroll-groups", "/api/reports/payroll",
            "/api/reports/gl-reconciliation", "/api/reports/loans", "/api/reports/labor-cost"),

    COMPENSATION("compensation", "Compensation", false, "/api/compensation"),

    /** Allowances + bonus runs. {@code PayrollEngine} reads employee allowances,
     *  so a plan with payroll but without this module can run payroll yet cannot
     *  configure what it pays — hence both ship together from LITE up. */
    BENEFITS("benefits", "Benefits", false, "/api/compbenefits"),

    BUDGETING("budgeting", "Budgeting", false, "/api/budgets"),

    RECRUITMENT("recruitment", "Recruitment", false,
            "/api/recruitment", "/api/reports/recruitment"),

    PERFORMANCE("performance", "Performance", false,
            "/api/performance", "/api/reports/performance"),

    TALENT_SUCCESSION("talent-succession", "Talent & Succession", false,
            "/api/talent", "/api/succession"),

    LEARNING_LMS("learning-lms", "Learning (LMS)", false, "/api/learning"),

    ENGAGEMENT("engagement", "Engagement", false, "/api/engagement"),

    HEALTH_SAFETY("health-safety", "Health & Safety", false, "/api/ehs"),

    COMPLIANCE("compliance", "Compliance", false, "/api/compliance"),

    REPORTS_ANALYTICS("reports-analytics", "Reports & Analytics", false,
            "/api/reports", "/api/analytics", "/api/dashboards", "/api/custom-reports"),

    WORKFLOW_APPROVALS("workflow-approvals", "Workflow & Approvals", false, "/api/workflow");

    private final String key;
    private final String label;
    private final boolean alwaysOn;
    private final List<String> apiPrefixes;

    HcmModule(String key, String label, boolean alwaysOn, String... apiPrefixes) {
        this.key = key;
        this.label = label;
        this.alwaysOn = alwaysOn;
        this.apiPrefixes = List.of(apiPrefixes);
    }

    /** Wire key — matches the frontend category key and the disabled_modules CSV. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** True when no plan and no tenant toggle may switch this module off. */
    public boolean alwaysOn() {
        return alwaysOn;
    }

    public List<String> apiPrefixes() {
        return apiPrefixes;
    }

    private static final Map<String, HcmModule> BY_KEY = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(HcmModule::key, m -> m));

    public static Optional<HcmModule> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** Modules no plan or tenant may disable. */
    public static List<HcmModule> alwaysOnModules() {
        return Stream.of(values()).filter(HcmModule::alwaysOn).toList();
    }
}
