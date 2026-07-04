package az.millers.hcm.lifecycle.domain;

/**
 * Onboarding category / stream a checklist task belongs to (M299 — Onboarding
 * Phase A.2, PRD §2). Distinct from {@link ChecklistTaskType} (what the task
 * is): the category is which onboarding stream / persona owns it, used to group
 * a journey into phases and to feed the persona dashboards (Phase E). Optional —
 * a task can be uncategorised. Onboarding-specific; offboarding tasks leave it null.
 */
public enum ChecklistOnboardingCategory {
    PRE_BOARDING,
    HR_ONBOARDING,
    MANAGER_ONBOARDING,
    IT_ONBOARDING,
    PAYROLL_ONBOARDING,
    FACILITIES_ONBOARDING,
    SECURITY_ONBOARDING,
    TRAINING_ONBOARDING,
    COMPLIANCE_ONBOARDING,
    FIRST_DAY_ONBOARDING,
    PROBATION_ONBOARDING
}
