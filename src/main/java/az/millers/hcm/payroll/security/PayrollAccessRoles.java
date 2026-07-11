package az.millers.hcm.payroll.security;

/**
 * M349 — Payroll salary visibility helper (single source of truth for salary masking).
 * HR_SPECIALIST can read payroll data but salary amounts are masked (returned as null).
 */
public final class PayrollAccessRoles {

    private PayrollAccessRoles() {}

    /**
     * @return true if the current user can see salary amounts (not masked).
     *         Roles with access: SYSTEM_ADMIN, HR_ADMIN, PAYROLL_SPECIALIST, AUDITOR.
     *         HR_SPECIALIST is intentionally EXCLUDED — they can read component
     *         catalog/assignments but amounts are masked. A privileged role ALWAYS
     *         wins even if the user also holds HR_SPECIALIST, so we scan every
     *         authority for a privileged grant before deciding to mask.
     */
    public static boolean canSeeSalaryAmounts() {
        // Delegates to the unified predicate (SYSTEM_ADMIN, HR_ADMIN,
        // PAYROLL_SPECIALIST, COMPENSATION_MANAGER, AUDITOR) so payroll and
        // compensation DTOs mask salary identically. HR_SPECIALIST stays masked.
        return az.millers.hcm.security.SalaryVisibility.canSeeSalaryAmounts();
    }
}
