package az.millers.hcm.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Keycloak realm-role assignment (M44).
 *
 * <p>All endpoints require {@code SYSTEM_ADMIN} — only the platform
 * administrator may view or modify user role assignments. The data is sourced
 * directly from Keycloak via {@link KeycloakAdminService}; no HCM database
 * record is created or modified.
 *
 * <p>Endpoints:
 * <pre>
 *   GET  /api/admin/users            — list all realm users with their roles
 *   GET  /api/admin/users/roles      — list assignable role names
 *   PUT  /api/admin/users/{id}/roles — replace a user's role set
 *   POST /api/admin/users/{id}/temporary-password — issue a one-time password
 *   GET  /api/admin/users/without-login — employees who have no account yet
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class UserManagementController {

    private final KeycloakAdminService keycloakAdminService;
    private final az.millers.hcm.audit.AuditService auditService;
    private final az.millers.hcm.corehr.repo.EmployeeRepository employees;

    public UserManagementController(KeycloakAdminService keycloakAdminService,
                                    az.millers.hcm.audit.AuditService auditService,
                                    az.millers.hcm.corehr.repo.EmployeeRepository employees) {
        this.keycloakAdminService = keycloakAdminService;
        this.auditService = auditService;
        this.employees = employees;
    }

    /**
     * Employees who have no sign-in account.
     *
     * <p>This screen lists Keycloak users, so anyone without a login never
     * appeared on it at all — and an employee missing from the list is
     * indistinguishable from one who was already dealt with. Returning them
     * separately keeps the two ideas distinct: these are people, not accounts.
     */
    @GetMapping("/without-login")
    public List<UnlinkedEmployeeDto> employeesWithoutLogin() {
        return employees.findByUsernameIsNullOrderByEmployeeNoAsc().stream()
                .map(UnlinkedEmployeeDto::from)
                .toList();
    }

    /** Returns all realm users with their currently assigned HCM roles. */
    @GetMapping
    public List<UserAdminDto> listUsers() {
        return keycloakAdminService.listUsers();
    }

    /** Returns the ordered list of assignable realm role names. */
    @GetMapping("/roles")
    public List<String> getAvailableRoles() {
        return keycloakAdminService.availableRoles();
    }

    /**
     * Atomically replaces the HCM realm roles for the given user.
     * Roles not in the {@link KeycloakAdminService#HCM_ROLES} list are ignored.
     *
     * @param userId  Keycloak user UUID
     * @param roles   complete target role set (e.g. {@code ["HR_SPECIALIST"]})
     */
    @PutMapping("/{userId}/roles")
    public ResponseEntity<Void> setUserRoles(
            @PathVariable String userId,
            @RequestBody List<String> roles) {
        keycloakAdminService.setUserRoles(userId, roles);
        return ResponseEntity.noContent().build();
    }

    /**
     * Issues a one-time password for a user who cannot sign in yet.
     *
     * <p>Accounts created for employees have no password at all — the employee
     * is meant to set their own from a Keycloak password-setup email. Where
     * that email cannot be sent (no SMTP on the realm), this is the way in:
     * the administrator reads the generated password once and passes it to the
     * employee, who is forced to replace it at first sign-in.
     *
     * <p>The value is returned exactly once and is not recoverable afterwards.
     * The audit entry records who did this to whom, never the password.
     */
    @PostMapping("/{userId}/temporary-password")
    public TemporaryPasswordResponse issueTemporaryPassword(@PathVariable String userId) {
        String password = keycloakAdminService.resetToTemporaryPassword(userId);
        auditService.record("ADMIN", "KeycloakUser", userId,
                "ISSUE_TEMPORARY_PASSWORD", null, "temporary password issued");
        return new TemporaryPasswordResponse(password);
    }

    /**
     * Shown once and never again. Kept as its own type so nothing accidentally
     * folds a password into a broader user payload that gets logged or cached.
     */
    public record TemporaryPasswordResponse(String temporaryPassword) {}
}
