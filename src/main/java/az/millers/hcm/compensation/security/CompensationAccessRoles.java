package az.millers.hcm.compensation.security;

/**
 * Compensation salary-visibility helper (single source of truth for salary masking) —
 * mirrors {@code payroll.security.PayrollAccessRoles}. HR_SPECIALIST (and any other role
 * that can merely READ compensation) may see structure and ratios but NOT raw employee
 * salary amounts, which are masked (returned as null).
 */
public final class CompensationAccessRoles {

    private CompensationAccessRoles() {}

    /**
     * @return true if the current user may see raw salary amounts (unmasked).
     *         Privileged roles: SYSTEM_ADMIN, HR_ADMIN, PAYROLL_SPECIALIST,
     *         COMPENSATION_MANAGER, AUDITOR. A privileged role always wins even if the
     *         user also holds HR_SPECIALIST, so scan every authority before masking.
     */
    public static boolean canSeeSalaryAmounts() {
        // Delegates to the unified predicate so compensation and payroll DTOs
        // mask salary identically (canonical set incl. COMPENSATION_MANAGER).
        return az.millers.hcm.security.SalaryVisibility.canSeeSalaryAmounts();
    }
}
