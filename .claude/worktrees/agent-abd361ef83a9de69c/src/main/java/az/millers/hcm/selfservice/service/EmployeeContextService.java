package az.millers.hcm.selfservice.service;

import org.springframework.stereotype.Service;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Resolves the {@link Employee} backing the currently authenticated user.
 *
 * <p>Employees opt in by having a {@code username} set on their row; the
 * V22 migration links the seeded {@code employee} user to EMP-00001 for
 * the demo. When SSO lands, the resolver swaps to Keycloak's
 * {@code preferred_username} claim — the surface stays the same.
 */
@Service
public class EmployeeContextService {

    private final EmployeeRepository employees;
    private final CurrentRequest currentRequest;

    public EmployeeContextService(EmployeeRepository employees, CurrentRequest currentRequest) {
        this.employees = employees;
        this.currentRequest = currentRequest;
    }

    /** Required: throws if there's no employee row linked to the current user. */
    public Employee currentEmployee() {
        String username = currentRequest.username();
        if (username == null || username.isBlank() || "system".equals(username)) {
            throw new BadRequestException("No authenticated employee in context");
        }
        return employees.findByUsername(username)
                .orElseThrow(() -> new BadRequestException(
                        "User '" + username + "' is not linked to any employee record. "
                        + "Ask HR to set the username on your employee profile."));
    }
}
