package az.millers.hcm.corehr.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who can read a given {@link EmployeeNote} (M72 / P2-10).
 *
 * <p>Visibility is enforced application-side via {@link #isVisibleToCurrentCaller()} —
 * the DB only pins the column to the enum string set. Putting the gate here
 * (rather than spreading it across the service + controller) means the
 * visibility check is one method call away from anywhere that lists notes.
 *
 * <p>Role hierarchy (most → least permissive):
 * <pre>
 *   ALL_HR            — DEPARTMENT_MANAGER (their reports) + HR_* + SYSTEM_ADMIN
 *   MANAGER_ONLY      — DEPARTMENT_MANAGER (their reports) + HR_ADMIN + SYSTEM_ADMIN
 *                       (HR_SPECIALIST excluded — manager-only is for line-manager
 *                       eyes when HR delegates a candid record)
 *   HR_ONLY           — HR_* + SYSTEM_ADMIN
 *   SYSTEM_ADMIN_ONLY — SYSTEM_ADMIN
 * </pre>
 */
public enum NoteVisibility {
    ALL_HR,
    MANAGER_ONLY,
    HR_ONLY,
    SYSTEM_ADMIN_ONLY;

    /**
     * Returns {@code true} iff the current Spring Security authentication holds
     * a role that matches this visibility level. Called by
     * {@code EmployeeNoteService.listFor} to filter the list — rows whose
     * visibility level rejects the caller are silently dropped.
     */
    public boolean isVisibleToCurrentCaller() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        boolean isSystemAdmin = false;
        boolean isHrAdmin = false;
        boolean isHrSpecialist = false;
        boolean isDeptManager = false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String r = a.getAuthority();
            switch (r) {
                case "ROLE_SYSTEM_ADMIN" -> isSystemAdmin = true;
                case "ROLE_HR_ADMIN" -> isHrAdmin = true;
                case "ROLE_HR_SPECIALIST" -> isHrSpecialist = true;
                case "ROLE_DEPARTMENT_MANAGER" -> isDeptManager = true;
                default -> { /* AUDITOR / EMPLOYEE — handled below */ }
            }
        }
        return switch (this) {
            case SYSTEM_ADMIN_ONLY -> isSystemAdmin;
            case HR_ONLY           -> isSystemAdmin || isHrAdmin || isHrSpecialist;
            case MANAGER_ONLY      -> isSystemAdmin || isHrAdmin || isDeptManager;
            case ALL_HR            -> isSystemAdmin || isHrAdmin || isHrSpecialist || isDeptManager;
        };
    }
}
