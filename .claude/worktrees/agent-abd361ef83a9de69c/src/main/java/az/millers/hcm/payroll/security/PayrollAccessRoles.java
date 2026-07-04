package az.millers.hcm.payroll.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * M349 — Payroll salary visibility helper (single source of truth for salary masking).
 * HR_SPECIALIST can read payroll data but salary amounts are masked (returned as null).
 */
public final class PayrollAccessRoles {

    private PayrollAccessRoles() {}

    private static final String ROLE_HR_SPECIALIST = "ROLE_HR_SPECIALIST";

    /**
     * @return true if the current user can see salary amounts (not masked).
     *         Roles with access: SYSTEM_ADMIN, HR_ADMIN, PAYROLL_SPECIALIST, AUDITOR.
     *         HR_SPECIALIST is intentionally EXCLUDED — they can read component
     *         catalog/assignments but amounts are masked.
     */
    public static boolean canSeeSalaryAmounts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String role = ga.getAuthority();
            if (role.equals(ROLE_HR_SPECIALIST)) {
                return false; // HR_SPECIALIST → masked
            }
            if (role.equals("ROLE_SYSTEM_ADMIN")
                    || role.equals("ROLE_HR_ADMIN")
                    || role.equals("ROLE_PAYROLL_SPECIALIST")
                    || role.equals("ROLE_AUDITOR")) {
                return true;
            }
        }
        return false;
    }
}
