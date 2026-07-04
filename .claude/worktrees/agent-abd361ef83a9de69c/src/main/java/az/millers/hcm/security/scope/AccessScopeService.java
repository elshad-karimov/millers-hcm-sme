package az.millers.hcm.security.scope;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.organization.repo.OrgUnitRepository;

/**
 * Resolves the current user's effective ABAC scope (PRD 14.9).
 *
 * <p>The scope is expressed as an {@code Optional<Set<UUID>>} of accessible
 * employee IDs:
 * <ul>
 *   <li>{@link Optional#empty()} — caller is unrestricted (SYSTEM_ADMIN /
 *       HR_ADMIN / HR_SPECIALIST / AUDITOR). The caller can read every row.
 *   <li>{@code Optional.of(set)} — caller is scoped. Only rows whose
 *       {@code employeeId} is in {@code set} are visible.
 * </ul>
 *
 * <p>Rules (first match wins):
 * <ul>
 *   <li>Caller has {@code SYSTEM_ADMIN} / {@code HR_ADMIN} /
 *       {@code AUDITOR} → unrestricted.
 *   <li>Caller has {@code HR_SPECIALIST} and is linked to an Employee
 *       whose {@code scope_org_unit_id} is non-null → org-unit scope
 *       (descendants of that unit + employees in them — recursive CTE
 *       on {@code parent_id}). HR specialists without a linked employee
 *       or without {@code scope_org_unit_id} stay unrestricted
 *       (back-compat for the seeded {@code hrspec} user).
 *   <li>Caller has {@code DEPARTMENT_MANAGER} and is linked to an Employee →
 *       scope = the transitive set of that employee's reporting chain plus
 *       themselves (recursive CTE on {@code manager_id}).
 *   <li>Caller has {@code EMPLOYEE} role and is linked to an Employee →
 *       scope = just their own employee id.
 *   <li>Otherwise (no roles, no linkage) → empty set (sees nothing).
 * </ul>
 *
 * <p>Callers typically branch on {@link #scopeForCurrentUser()}: if empty,
 * skip the {@code WHERE employee_id IN (...)} predicate entirely; if
 * present, apply it. {@link #filterAccessible(Set, UUID)} is the helper
 * for spot-checks on single rows.
 *
 * <p>Stateless — recomputes on every call. Cheap (a single recursive CTE
 * for managers, a single lookup for employees). If profiling shows this
 * is hot, mark request-scope; the surface is small.
 */
@Service
public class AccessScopeService {

    /** Roles that grant unconditional, full-org visibility. */
    private static final Set<String> WIDE_ROLES = Set.of(
            "ROLE_SYSTEM_ADMIN",
            "ROLE_HR_ADMIN",
            "ROLE_AUDITOR");

    private static final String HR_SPECIALIST_ROLE = "ROLE_HR_SPECIALIST";
    private static final String DEPT_MANAGER_ROLE = "ROLE_DEPARTMENT_MANAGER";
    private static final String EMPLOYEE_ROLE = "ROLE_EMPLOYEE";

    private final EmployeeRepository employees;
    private final OrgUnitRepository orgUnits;
    private final WorkflowSubjectResolver workflowSubjects;

    public AccessScopeService(EmployeeRepository employees,
                              OrgUnitRepository orgUnits,
                              WorkflowSubjectResolver workflowSubjects) {
        this.employees = employees;
        this.orgUnits = orgUnits;
        this.workflowSubjects = workflowSubjects;
    }

    /**
     * @return empty when the caller has unrestricted access; otherwise the
     *         (possibly empty) set of employee IDs the caller can see.
     */
    public Optional<Set<UUID>> scopeForCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.of(Set.of());
        }
        Set<String> authorities = new HashSet<>();
        for (GrantedAuthority ga : auth.getAuthorities()) {
            authorities.add(ga.getAuthority());
        }
        // Wide roles win — SYSTEM_ADMIN / HR_ADMIN / AUDITOR are always
        // unrestricted (HR-admins who happen to line-manage someone don't
        // lose their HR view).
        for (String wide : WIDE_ROLES) {
            if (authorities.contains(wide)) {
                return Optional.empty();
            }
        }
        // HR_SPECIALIST: optionally org-unit scoped via the linked
        // employee's scope_org_unit_id. Without the linkage or anchor
        // set, falls through to unrestricted (back-compat — PRD 14.9).
        Optional<Employee> me = currentEmployee(auth.getName());
        if (authorities.contains(HR_SPECIALIST_ROLE)) {
            if (me.isPresent() && me.get().getScopeOrgUnitId() != null) {
                return Optional.of(scopeForOrgUnit(me.get().getScopeOrgUnitId()));
            }
            return Optional.empty();
        }
        // Manager scope: transitive reporting chain + self.
        if (authorities.contains(DEPT_MANAGER_ROLE) && me.isPresent()) {
            List<UUID> chain = employees.descendantsIncluding(me.get().getId());
            return Optional.of(new HashSet<>(chain));
        }
        // Employee scope: just self.
        if (authorities.contains(EMPLOYEE_ROLE) && me.isPresent()) {
            return Optional.of(Set.of(me.get().getId()));
        }
        // No applicable role and/or no employee linkage — see nothing.
        return Optional.of(Set.of());
    }

    /**
     * Materialise an HR specialist's org-unit scope: descendants of
     * {@code rootUnitId} (including itself) → employees assigned to any
     * of those units. Returns an empty set when the unit has no
     * descendants and no direct employees — caller sees nothing.
     */
    private Set<UUID> scopeForOrgUnit(UUID rootUnitId) {
        List<UUID> unitIds = orgUnits.descendantUnitIds(rootUnitId);
        if (unitIds.isEmpty()) return Set.of();
        return new HashSet<>(employees.findIdsByOrgUnitIdIn(unitIds));
    }

    /**
     * Convenience for single-row checks. Returns {@code true} when the row
     * with {@code targetEmployeeId} is visible to the current caller.
     */
    public boolean isAccessible(UUID targetEmployeeId) {
        if (targetEmployeeId == null) return false;
        Optional<Set<UUID>> scope = scopeForCurrentUser();
        return scope.isEmpty() || scope.get().contains(targetEmployeeId);
    }

    /** True iff the caller has no scope filter (HR / admin / auditor). */
    public boolean isUnrestricted() {
        return scopeForCurrentUser().isEmpty();
    }

    /**
     * Helper for repository code that just needs a set or {@code null} —
     * mirrors the Spring Data convention of passing {@code null} to mean
     * "no filter".
     */
    public Set<UUID> scopeOrNullForCurrentUser() {
        return scopeForCurrentUser().orElse(null);
    }

    /**
     * Trim a caller-supplied set to what they're actually allowed to see.
     * Use this when a list endpoint already accepts an {@code employeeId}
     * filter — combine the two.
     */
    public Set<UUID> intersect(Set<UUID> requested) {
        Optional<Set<UUID>> scope = scopeForCurrentUser();
        if (scope.isEmpty()) return requested; // unrestricted
        if (requested == null || requested.isEmpty()) return scope.get();
        Set<UUID> out = new HashSet<>(requested);
        out.retainAll(scope.get());
        return out;
    }

    /**
     * Decide whether the current caller is allowed to see a workflow
     * approval task for the given subject (PRD 14.9 — extended ABAC).
     *
     * <ul>
     *   <li>Unrestricted callers always pass.
     *   <li>Subjects whose entity isn't employee-scoped (e.g.
     *       {@code OrgVersion}, {@code PayrollRun}) pass — role checks
     *       gate them.
     *   <li>Employee-scoped subjects resolve to their owning
     *       {@code employee_id} via {@link WorkflowSubjectResolver};
     *       the caller's scope set must contain it.
     *   <li>Failure to resolve (subject deleted, malformed id) hides
     *       the row — err on the side of less leakage.
     * </ul>
     */
    public boolean isWorkflowSubjectAccessible(String entity, String subjectIdStr) {
        if (isUnrestricted()) return true;
        if (!workflowSubjects.isEmployeeScoped(entity)) {
            // Not employee-scoped → role-gated only. The inbox query has
            // already filtered by role, so let it through.
            return true;
        }
        return workflowSubjects
                .resolveEmployeeId(entity, subjectIdStr)
                .map(this::isAccessible)
                .orElse(false);
    }

    private Optional<Employee> currentEmployee(String username) {
        if (username == null || username.isBlank() || "system".equals(username)) {
            return Optional.empty();
        }
        return employees.findByUsername(username);
    }
}
