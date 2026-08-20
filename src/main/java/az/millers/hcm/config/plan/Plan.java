package az.millers.hcm.config.plan;

import java.util.EnumSet;
import java.util.Set;

/**
 * Commercial edition a tenant is on. The plan decides which {@link HcmModule}s
 * the tenant may use at all; within that set the tenant's own
 * {@code disabled_modules} setting decides what it chooses to show.
 *
 * <p>Two distinct reasons a module can be off, and they read very differently to
 * a user: {@code NOT_IN_PLAN} (upsell — "available in STANDARD") versus
 * {@code DISABLED_BY_TENANT} (their own admin switched it off). See
 * {@link ModuleAccessService.Reason}.
 *
 * <p>This enum is the single source of truth. The SPA renders from
 * {@code /api/module-settings} rather than keeping its own copy of the tiering.
 */
public enum Plan {

    /**
     * The SME edition: everything needed to run HR day one — people, org,
     * hire-to-exit, time, leave, payroll (with the allowance config payroll
     * depends on), approvals, and reporting.
     */
    LITE(EnumSet.of(
            HcmModule.SELF_SERVICE,
            HcmModule.PLATFORM_ADMIN,
            HcmModule.MANAGER_SELF_SERVICE,
            HcmModule.CORE_HR_EMPLOYEE_MANAGEMENT,
            HcmModule.CORE_HR_ORGANIZATION,
            HcmModule.EMPLOYEE_LIFECYCLE,
            HcmModule.TIME_ATTENDANCE,
            HcmModule.LEAVE_ABSENCE,
            HcmModule.PAYROLL,
            HcmModule.BENEFITS,
            HcmModule.WORKFLOW_APPROVALS,
            HcmModule.REPORTS_ANALYTICS),
            PlanLimits.unlimited()),

    /** LITE plus the talent-acquisition and HR-operations surface. */
    STANDARD(EnumSet.of(
            HcmModule.SELF_SERVICE,
            HcmModule.PLATFORM_ADMIN,
            HcmModule.MANAGER_SELF_SERVICE,
            HcmModule.CORE_HR_EMPLOYEE_MANAGEMENT,
            HcmModule.CORE_HR_ORGANIZATION,
            HcmModule.CORE_HR_STAFFING_POSITIONS,
            HcmModule.CORE_HR_HR_OPERATIONS,
            HcmModule.EMPLOYEE_LIFECYCLE,
            HcmModule.TIME_ATTENDANCE,
            HcmModule.LEAVE_ABSENCE,
            HcmModule.TRAVEL_EXPENSE,
            HcmModule.PAYROLL,
            HcmModule.BENEFITS,
            HcmModule.RECRUITMENT,
            HcmModule.PERFORMANCE,
            HcmModule.LEARNING_LMS,
            HcmModule.COMPLIANCE,
            HcmModule.WORKFLOW_APPROVALS,
            HcmModule.REPORTS_ANALYTICS),
            PlanLimits.unlimited()),

    /** The full product — every module. */
    ENTERPRISE(EnumSet.allOf(HcmModule.class), PlanLimits.unlimited());

    private final Set<HcmModule> modules;
    private final PlanLimits limits;

    Plan(Set<HcmModule> modules, PlanLimits limits) {
        this.modules = Set.copyOf(modules);
        this.limits = limits;
    }

    /** Modules this plan entitles the tenant to. */
    public Set<HcmModule> modules() {
        return modules;
    }

    public PlanLimits limits() {
        return limits;
    }

    public boolean includes(HcmModule module) {
        return module.alwaysOn() || modules.contains(module);
    }

    /** The default for a newly provisioned tenant in this (SME) edition. */
    public static Plan defaultPlan() {
        return LITE;
    }

    /** Lenient parse for persisted / request values; unknown or blank → LITE. */
    public static Plan parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultPlan();
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return defaultPlan();
        }
    }

    /** Cheapest plan that includes {@code module} — the upsell target. */
    public static Plan lowestPlanWith(HcmModule module) {
        for (Plan p : values()) {
            if (p.includes(module)) {
                return p;
            }
        }
        return ENTERPRISE;
    }
}
