package az.millers.hcm.apikey.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.security.SecurityRoles;

/**
 * M120 — gatekeeper between the role names a key wants and the role names
 * the platform recognises. Keeps the whitelist tight so an admin can't
 * issue a key for a typo'd role and only learn at request time.
 *
 * <p>Pure-static; tests instantiate scopes by raw strings.
 */
public final class ApiKeyScopes {

    /** The full set of role names the JWT layer also accepts. */
    public static final Set<String> ALL_SCOPES = Set.of(
            SecurityRoles.R_SYSTEM_ADMIN,
            SecurityRoles.R_HR_ADMIN,
            SecurityRoles.R_HR_SPECIALIST,
            SecurityRoles.R_AUDITOR,
            SecurityRoles.R_RECRUITER,
            SecurityRoles.R_DEPARTMENT_MANAGER,
            SecurityRoles.R_EMPLOYEE,
            SecurityRoles.R_OCCUPATIONAL_HEALTH,
            SecurityRoles.R_PAYROLL_SPECIALIST,
            SecurityRoles.R_FINANCE_USER
    );

    private ApiKeyScopes() {}

    /**
     * Normalise + validate a requested scope list. Strips blanks, dedupes,
     * rejects anything not in {@link #ALL_SCOPES}, and throws
     * {@link BadRequestException} on the first unknown scope so the admin
     * sees a useful message.
     */
    public static List<String> normalise(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new BadRequestException("At least one scope is required");
        }
        Set<String> out = new LinkedHashSet<>();
        for (String raw : requested) {
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            if (!ALL_SCOPES.contains(trimmed)) {
                throw new BadRequestException("Unknown scope: " + trimmed);
            }
            out.add(trimmed);
        }
        if (out.isEmpty()) {
            throw new BadRequestException("At least one scope is required");
        }
        return List.copyOf(out);
    }
}
