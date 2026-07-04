package az.millers.hcm.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Single source of truth for which roles see decrypted PII in API responses.
 *
 * <p>Extracted from inline copies in {@code EmployeeIdentificationService} and
 * {@code EmployeeCertificationService} during M71 — adding two more services
 * with the same logic (dependents, work experience) made the duplication
 * acute. One place to widen / narrow the PII gate going forward.
 *
 * <p>SYSTEM_ADMIN, HR_ADMIN, and AUDITOR see plaintext document numbers,
 * salaries, encrypted national IDs, etc. Every other role (HR_SPECIALIST,
 * DEPARTMENT_MANAGER, EMPLOYEE, OCCUPATIONAL_HEALTH) gets the masked
 * representation. The masking itself is the response DTO's responsibility —
 * this class only answers the boolean.
 */
public final class PiiAccessRoles {

    private PiiAccessRoles() {}

    /**
     * @return {@code true} iff the current Spring Security authentication has
     *         one of the cleared roles. {@code false} for unauthenticated
     *         contexts (defensive — should never happen in a controller path).
     */
    public static boolean callerCanSeePlaintextPii() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String r = a.getAuthority();
            if ("ROLE_SYSTEM_ADMIN".equals(r)
                    || "ROLE_HR_ADMIN".equals(r)
                    || "ROLE_AUDITOR".equals(r)) {
                return true;
            }
        }
        return false;
    }
}
