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
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class UserManagementController {

    private final KeycloakAdminService keycloakAdminService;

    public UserManagementController(KeycloakAdminService keycloakAdminService) {
        this.keycloakAdminService = keycloakAdminService;
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
}
