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

    /**
     * @return true if the current user can see salary amounts (not masked).
     *         Roles with access: SYSTEM_ADMIN, HR_ADMIN, PAYROLL_SPECIALIST, AUDITOR.
     *         HR_SPECIALIST is intentionally EXCLUDED — they can read component
     *         catalog/assignments but amounts are masked. A privileged role ALWAYS
     *         wins even if the user also holds HR_SPECIALIST, so we scan every
     *         authority for a privileged grant before deciding to mask.
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
                    || role.equals("ROLE_AUDITOR")) {
                return true;
            }
        }
        return false; // HR_SPECIALIST and everyone else → masked
    }
}
