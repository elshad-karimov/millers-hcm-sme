package az.millers.hcm.compensation.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String role = ga.getAuthority();
            if (role.equals("ROLE_SYSTEM_ADMIN")
                    || role.equals("ROLE_HR_ADMIN")
                    || role.equals("ROLE_PAYROLL_SPECIALIST")
                    || role.equals("ROLE_COMPENSATION_MANAGER")
                    || role.equals("ROLE_AUDITOR")) {
                return true;
            }
        }
        return false; // HR_SPECIALIST, FINANCE_USER, managers, employees → masked
    }
}
