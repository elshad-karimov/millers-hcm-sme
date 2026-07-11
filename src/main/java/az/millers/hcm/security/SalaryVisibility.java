package az.millers.hcm.security;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Single source of truth for "may the current user see raw salary amounts?".
 *
 * <p>Previously this predicate was implemented twice — {@code payroll.security
 * .PayrollAccessRoles} and {@code compensation.security.CompensationAccessRoles}
 * each claimed to be the single source of truth, and the two role sets had drifted
 * (Compensation granted {@code COMPENSATION_MANAGER}, Payroll did not), so a
 * compensation manager saw raw salary on comp DTOs but masked salary on payroll
 * DTOs. Both classes now delegate here so the answer is identical everywhere.
 *
 * <p>Canonical privileged set: SYSTEM_ADMIN, HR_ADMIN, PAYROLL_SPECIALIST,
 * COMPENSATION_MANAGER, AUDITOR. HR_SPECIALIST (and anyone who can merely read
 * payroll/comp) sees structure but not raw amounts. A privileged grant always
 * wins even if the user also holds a masked role.
 */
public final class SalaryVisibility {

    private SalaryVisibility() {}

    private static final Set<String> PRIVILEGED = Set.of(
            "ROLE_SYSTEM_ADMIN",
            "ROLE_HR_ADMIN",
            "ROLE_PAYROLL_SPECIALIST",
            "ROLE_COMPENSATION_MANAGER",
            "ROLE_AUDITOR");

    /** @return true if the current user may see unmasked salary amounts. */
    public static boolean canSeeSalaryAmounts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (PRIVILEGED.contains(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
