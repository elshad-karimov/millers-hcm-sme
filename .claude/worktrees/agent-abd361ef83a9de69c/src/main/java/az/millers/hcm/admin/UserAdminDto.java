package az.millers.hcm.admin;

import java.util.List;

/**
 * Projection returned by {@link UserManagementController} — a Keycloak user
 * with the list of realm roles currently assigned to them.
 */
public record UserAdminDto(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        List<String> roles
) {}
